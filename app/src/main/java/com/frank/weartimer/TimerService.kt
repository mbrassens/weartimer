package com.frank.weartimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity

class TimerService : Service() {
    private var countDownTimer: CountDownTimer? = null
    private var toneGen: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var buzzingTimer: CountDownTimer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ongoingActivity: OngoingActivity? = null
    private var isStopping = false
    private var wakeLockReleased = false

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "timer_channel"
    private val PREFS_NAME = "timer_state"
    private val KEY_TIMER_RUNNING = "timer_running"
    private val KEY_TIMER_FINISHED = "timer_finished"

    companion object {
        const val EXTRA_TIMER_DURATION = "timer_duration"
        const val ACTION_STOP_TIMER = "stop_timer"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TIMER -> {
                stopTimer()
                return START_NOT_STICKY
            }
            else -> {
                val timerDuration = intent?.getIntExtra(EXTRA_TIMER_DURATION, 60) ?: 60
                startTimer(timerDuration)
                return START_STICKY
            }
        }
    }

    private fun startTimer(duration: Int) {
        // Mark timer as running
        setTimerRunning(true)
        setTimerFinished(false)
        saveTimerState(duration)

        // Start ongoing activity
        startRunningOngoingActivity()

        // Start the countdown timer
        countDownTimer = object : CountDownTimer((duration * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update remaining time in SharedPreferences
                val remainingSeconds = (millisUntilFinished / 1000).toInt()
                updateRemainingTime(remainingSeconds)
                TimerOldTileService.requestTileUpdate(applicationContext)
                TimerTileService.requestTileUpdate(applicationContext)
            }

            override fun onFinish() {
                // Timer finished
                println("DEBUG: Timer finished, setting state")
                setTimerRunning(false)
                setTimerFinished(true)
                // Don't clear timer state yet - keep it until user stops the alarm
                
                println("DEBUG: Timer state set - running: false, finished: true")
                
                // Start buzzing
                startBuzzing()
                
                // Update notification
                updateOngoingActivityForFinish()
                TimerOldTileService.requestTileUpdate(applicationContext)
                TimerTileService.requestTileUpdate(applicationContext)
            }
        }.start()

        // Start foreground service
        startForeground(NOTIFICATION_ID, createRunningNotification().build())
    }

    private fun stopTimer() {
        isStopping = true

        // Stop timers
        countDownTimer?.cancel()
        buzzingTimer?.cancel()

        // Release resources
        toneGen?.release()
        if (wakeLock != null && !wakeLockReleased) {
            wakeLock?.release()
            wakeLockReleased = true
        }

        // Clear state
        setTimerRunning(false)
        setTimerFinished(false)
        clearTimerState() // Clear timer state when user stops the alarm

        // Clear ongoing activity
        ongoingActivity = null
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        TimerOldTileService.requestTileUpdate(applicationContext)
        TimerTileService.requestTileUpdate(applicationContext)

        // Stop service
        stopForeground(true)
        stopSelf()
    }

    private fun startBuzzing() {
        // Acquire wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Weartimer::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)

        // Start buzzing
        toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        vibrator = getSystemService(Vibrator::class.java)

        buzzingTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isStopping) {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                    vibrator?.vibrate(500)
                }
            }
            override fun onFinish() {
                // This won't be called since we're using Long.MAX_VALUE
            }
        }.start()
    }

    private fun startRunningOngoingActivity() {
        ongoingActivity = OngoingActivity.Builder(
            this,
            NOTIFICATION_ID,
            createRunningNotification()
        ).setTouchIntent(createPendingIntent())
         .build()

        ongoingActivity?.apply(this)
    }

    private fun updateOngoingActivityForFinish() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createFinishedNotification().build())
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for timer status"
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createRunningNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏱️ Timer Running")
            .setContentText("Tap to view timer")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setOngoing(true)
    }

    private fun createFinishedNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ Timer Finished!")
            .setContentText("Tap to stop alarm")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, CountdownActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setTimerRunning(running: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TIMER_RUNNING, running).apply()
    }

    private fun setTimerFinished(finished: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TIMER_FINISHED, finished).apply()
        println("DEBUG: setTimerFinished called with: $finished")
    }

    private fun saveTimerState(duration: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit()
            .putLong("timer_start_time", currentTime)
            .putInt("timer_duration", duration)
            .putLong("timer_end_time", currentTime + (duration * 1000L))
            .apply()
    }

    private fun updateRemainingTime(remainingSeconds: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        val endTime = currentTime + (remainingSeconds * 1000L)
        prefs.edit().putLong("timer_end_time", endTime).apply()
    }



    private fun clearTimerState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("timer_start_time")
            .remove("timer_duration")
            .remove("timer_end_time")
            .apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }
}