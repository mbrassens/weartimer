package com.frank.weartimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import androidx.wear.ongoing.OngoingActivity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CountdownActivity : ComponentActivity() {
    private var toneGen: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var buzzingTimer: CountDownTimer? = null
    private var isStopping = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var ongoingActivity: OngoingActivity? = null
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "timer_channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val timerLength = intent.getIntExtra("timer_length", 60)
        setContent {
            CountdownScreen(
                initialTime = timerLength,
                onTimerStarted = { startRunningOngoingActivity() },
                onTimerFinished = { updateOngoingActivityForFinish() },
                onStopPressed = { stopBuzzing() }
            )
        }
    }

    private fun startRunningOngoingActivity() {
        try {
            println("DEBUG: Creating running ongoing activity")
            
            // Create the ongoing activity for running timer
            ongoingActivity = OngoingActivity.Builder(
                this,
                NOTIFICATION_ID,
                createRunningNotification()
            ).setTouchIntent(createPendingIntent())
             .build()
            
            println("DEBUG: OngoingActivity builder created")
            
            // Apply the ongoing activity
            ongoingActivity?.apply(this)
            println("DEBUG: OngoingActivity applied successfully")
            
            // Also send the notification directly to ensure it shows
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createRunningNotification().build())
            println("DEBUG: Notification sent directly")
            
        } catch (e: Exception) {
            println("DEBUG: OngoingActivity failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun updateOngoingActivityForFinish() {
        if (isStopping) return
        
        // Start ongoing activity for Wear OS
        startOngoingActivity()
        
        // Acquire wake lock to keep screen on
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Weartimer::WakeLock"
        )
        wakeLock?.acquire(10*60*1000L) // 10 minutes timeout
        println("DEBUG: Wake lock acquired")
        
        // Set window flags to bring to front
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        println("DEBUG: Window flags set")
        
        // Bring activity to front
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            println("DEBUG: setShowWhenLocked and setTurnScreenOn called")
        }
        
        // Force the activity to be visible and focused
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        
        // Request focus to bring to front
        try {
            window.decorView.requestFocus()
            println("DEBUG: Requested focus")
        } catch (e: Exception) {
            println("DEBUG: Request focus failed: ${e.message}")
        }
        
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
        println("DEBUG: Buzzing timer started")
    }

    private fun startOngoingActivity() {
        try {
            // Create the ongoing activity for finished timer
            ongoingActivity = OngoingActivity.Builder(
                this,
                NOTIFICATION_ID,
                createFinishedNotification()
            ).setTouchIntent(createPendingIntent())
             .build()
            
            // Apply the ongoing activity
            ongoingActivity?.apply(this)
            println("DEBUG: OngoingActivity updated for finished timer")
        } catch (e: Exception) {
            println("DEBUG: OngoingActivity failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createRunningNotification(): NotificationCompat.Builder {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Timer notifications"
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timer finished notifications"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
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

    private fun stopBuzzing() {
        println("DEBUG: stopBuzzing called")
        isStopping = true
        buzzingTimer?.cancel()
        toneGen?.release()
        toneGen = null
        
        // Release wake lock
        wakeLock?.release()
        wakeLock = null
        
        // Clear ongoing activity by canceling notification
        ongoingActivity = null
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        isStopping = true
        buzzingTimer?.cancel()
        toneGen?.release()
        wakeLock?.release()
        ongoingActivity = null
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

@Composable
fun CountdownScreen(
    initialTime: Int,
    onTimerStarted: () -> Unit,
    onTimerFinished: () -> Unit,
    onStopPressed: () -> Unit
) {
    var timeLeft by remember { mutableStateOf(initialTime) }
    var preciseTimeLeft by remember { mutableStateOf(initialTime.toFloat()) }
    var isRunning by remember { mutableStateOf(true) }
    var isFinished by remember { mutableStateOf(false) }
    var timer: CountDownTimer? by remember { mutableStateOf(null) }
    
    // Calculate progress for the arc using precise time (0.0 to 1.0) with smooth animation
    val effectiveInitialTime = initialTime.toFloat()
    val targetProgress by remember {
        derivedStateOf {
            if (isFinished) 0f else if (preciseTimeLeft > 0f) {
                val progress = preciseTimeLeft / effectiveInitialTime
                if (progress > 1f) 1f else progress
            } else 0f
        }
    }
    
    // Force animation restart with a key
    val animationKey = remember { isRunning }
    val animatedProgressWithKey by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )
    
    // Force animation restart with a key
    val animationKey2 = remember { isRunning }
    val animatedProgressWithKey2 by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )
    
    // Use the second animation for the canvas
    val finalAnimatedProgress = animatedProgressWithKey2

    // Flashing animation
    val infiniteTransition = rememberInfiniteTransition()
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        )
    )

    DisposableEffect(isRunning) {
        if (isRunning) {
            // Call onTimerStarted when timer begins
            onTimerStarted()
            
            // Start the timer immediately
            timer = object : CountDownTimer((initialTime * 1000).toLong(), 100) {
                override fun onTick(millisUntilFinished: Long) {
                    // Use precise time for animation (update more frequently for smoother animation)
                    preciseTimeLeft = millisUntilFinished / 1000f
                    // Use actual time for display
                    timeLeft = (millisUntilFinished / 1000).toInt()
                }
                override fun onFinish() {
                    timeLeft = 0
                    preciseTimeLeft = 0f
                    isRunning = false
                    isFinished = true
                    onTimerFinished()
                }
            }.start()
        } else {
            timer?.cancel()
        }
        onDispose { 
            timer?.cancel()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isFinished) {
            // Flashing red screen with stop button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = flashAlpha))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = "TIME'S UP!",
                        fontSize = 28.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 48.dp)
                    )
                    
                    Button(
                        onClick = onStopPressed,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .fillMaxWidth(0.7f)
                            .height(64.dp)
                    ) {
                        Text(
                            "STOP",
                            fontSize = 24.sp,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            // Timer countdown with animated circular progress arc
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Animated circular progress arc
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val strokeWidth = 8.dp.toPx()
                    val radius = (size.minDimension / 2) - strokeWidth
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                    
                    // Draw the progress arc with smooth animation
                    if (finalAnimatedProgress > 0f) {
                        val sweepAngle = finalAnimatedProgress * 360f
                        drawArc(
                            color = Color.Blue,
                            startAngle = -90f, // Start from top
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                center.x - radius,
                                center.y - radius
                            ),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
                
                // Timer text in the center (using rounded-up display value)
                Text(
                    text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                    fontSize = 36.sp,
                    color = Color.White
                )
            }
        }
    }
} 