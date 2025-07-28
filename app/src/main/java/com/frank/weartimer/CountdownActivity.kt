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
import androidx.wear.ongoing.Status
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
    private val PREFS_NAME = "timer_state"
    private val KEY_TIMER_RUNNING = "timer_running"
    private val KEY_TIMER_FINISHED = "timer_finished"
    private val KEY_TIMER_START_TIME = "timer_start_time"
    private val KEY_TIMER_DURATION = "timer_duration"
    private val KEY_TIMER_END_TIME = "timer_end_time"

    companion object {
        const val EXTRA_TIMER_STATE = "timer_state"
        const val STATE_RUNNING = 0
        const val STATE_FINISHED = 1
        
        fun isTimerRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences("timer_state", Context.MODE_PRIVATE)
            return prefs.getBoolean("timer_running", false)
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Notifications",
                NotificationManager.IMPORTANCE_LOW // Default importance for running timer
            ).apply {
                description = "Notifications for timer status"
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getRemainingTime(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong(KEY_TIMER_END_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val remainingMillis = endTime - currentTime
        return if (remainingMillis > 0) (remainingMillis / 1000).toInt() else 0
    }

    private fun getOriginalTimerDuration(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TIMER_DURATION, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if there's an existing timer running
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isTimerRunning = prefs.getBoolean(KEY_TIMER_RUNNING, false)
        val isTimerFinished = prefs.getBoolean(KEY_TIMER_FINISHED, false)
        
        println("DEBUG: onCreate - isTimerRunning: $isTimerRunning, isTimerFinished: $isTimerFinished")
        
        var timerLength: Int
        var timerState: Int
        var originalDuration: Int
        
        // Check if this is a request to just view the current timer (from tile)
        val requestedTimerLength = intent.getIntExtra("timer_length", 60)
        
        println("DEBUG: onCreate - requestedTimerLength: $requestedTimerLength")
        
        if (requestedTimerLength == -1) {
            // Just view current timer (from tile when timer is running)
            if (isTimerRunning && !isTimerFinished) {
                timerLength = getRemainingTime()
                timerState = STATE_RUNNING
                originalDuration = getOriginalTimerDuration()
                println("DEBUG: Viewing current timer with ${timerLength} seconds remaining")
            } else if (isTimerFinished) {
                // Timer has finished, show the finished screen
                timerLength = 0
                timerState = STATE_FINISHED
                originalDuration = getOriginalTimerDuration()
                println("DEBUG: Showing finished timer screen")
            } else {
                // No timer running, go back to main activity
                println("DEBUG: No timer running, going back to MainActivity")
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finish()
                return
            }
        } else if (isTimerRunning && !isTimerFinished) {
            // Restore existing timer
            timerLength = getRemainingTime()
            timerState = STATE_RUNNING
            originalDuration = getOriginalTimerDuration()
            println("DEBUG: Restoring timer with ${timerLength} seconds remaining")
        } else if (isTimerFinished) {
            // Timer has finished, show the finished screen
            timerLength = 0
            timerState = STATE_FINISHED
            originalDuration = getOriginalTimerDuration()
            println("DEBUG: Showing finished timer screen")
        } else {
            // Start new timer
            timerLength = requestedTimerLength
            timerState = intent.getIntExtra(EXTRA_TIMER_STATE, STATE_RUNNING)
            originalDuration = timerLength
            
            // Start the TimerService for new timers
            if (timerState == STATE_RUNNING) {
                val serviceIntent = Intent(this, TimerService::class.java)
                serviceIntent.putExtra(TimerService.EXTRA_TIMER_DURATION, timerLength)
                startService(serviceIntent)
            }
        }

        println("DEBUG: onCreate - final timerLength: $timerLength, timerState: $timerState")

        // Create notification channel once
        createNotificationChannel()

        setContent {
            CountdownScreen(
                initialTime = timerLength,
                originalDuration = originalDuration,
                initialStateIsFinished = timerState == STATE_FINISHED,
                onTimerStarted = { /* Service handles this */ },
                onTimerFinished = { /* Service handles this */ },
                onStopPressed = { stopTimer() }
            )
        }
    }

    override fun onBackPressed() {
        // Check if timer is finished
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isTimerFinished = prefs.getBoolean(KEY_TIMER_FINISHED, false)
        
        if (isTimerFinished) {
            // If timer is finished, swipe to dismiss closes the app
            finishAffinity()
        } else {
            // If timer is running, return to watch face (home screen)
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
            finish()
        }
    }

    private fun stopTimer() {
        // Stop the TimerService
        val serviceIntent = Intent(this, TimerService::class.java)
        serviceIntent.action = TimerService.ACTION_STOP_TIMER
        startService(serviceIntent)
        
        // Close the app completely
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service handles cleanup
    }
}

@Composable
fun CountdownScreen(
    initialTime: Int,
    originalDuration: Int,
    initialStateIsFinished: Boolean,
    onTimerStarted: () -> Unit,
    onTimerFinished: () -> Unit,
    onStopPressed: () -> Unit
) {
    var timeLeft by remember { mutableStateOf(initialTime) }
    var preciseTimeLeft by remember { mutableStateOf(initialTime.toFloat()) }
    var isRunning by remember { mutableStateOf(!initialStateIsFinished) }
    var isFinished by remember { mutableStateOf(initialStateIsFinished) }
    var timer: CountDownTimer? by remember { mutableStateOf(null) }

    println("DEBUG: CountdownScreen - initialTime: $initialTime, originalDuration: $originalDuration, initialStateIsFinished: $initialStateIsFinished")
    println("DEBUG: CountdownScreen - isRunning: $isRunning, isFinished: $isFinished")

    // If starting in finished state, immediately call onTimerFinished
    LaunchedEffect(initialStateIsFinished) {
        if (initialStateIsFinished) {
            println("DEBUG: CountdownScreen - LaunchedEffect called onTimerFinished")
            onTimerFinished()
        }
    }

    // Calculate progress for the arc using precise time (0.0 to 1.0) with smooth animation
    val effectiveInitialTime = originalDuration.toFloat()
    val targetProgress by remember {
        derivedStateOf {
            if (isFinished) 0f else if (preciseTimeLeft > 0f) {
                val progress = preciseTimeLeft / effectiveInitialTime
                if (progress > 1f) 1f else progress
            } else 0f
        }
    }

    // Force animation restart with a key (Only one needed for progress)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )

    // Flashing animation
    val infiniteTransition = rememberInfiniteTransition(label = "flashTransition")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ), label = "flashAlpha"
    )

    DisposableEffect(isRunning) {
        if (isRunning) {
            // Call onTimerStarted only if truly starting from a running state
            if (!initialStateIsFinished) {
                onTimerStarted()
            }

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
        println("DEBUG: CountdownScreen - Rendering UI, isFinished: $isFinished")
        
        if (isFinished) {
            println("DEBUG: CountdownScreen - Showing finished timer screen")
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
            println("DEBUG: CountdownScreen - Showing running timer screen")
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
                    if (animatedProgress > 0f) {
                        val sweepAngle = animatedProgress * 360f
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