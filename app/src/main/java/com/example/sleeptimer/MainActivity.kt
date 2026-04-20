package com.example.sleeptimer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.sleeptimer.ui.theme.SleeptimerTheme
import com.example.sleeptimer.ui.theme.SleepAccentPink
import com.example.sleeptimer.ui.theme.SleepPrimary
import com.example.sleeptimer.ui.theme.SleepSecondary
import com.example.sleeptimer.ui.theme.SleepTextDim
import com.example.sleeptimer.ui.theme.SleepTextSecondary
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            SleeptimerTheme(darkTheme = true) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SleepTimerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SleepTimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)

    // Timer values
    var hours by remember { mutableIntStateOf(prefs.getInt("hours", 0)) }
    var minutes by remember { mutableIntStateOf(prefs.getInt("minutes", 30)) }
    var seconds by remember { mutableIntStateOf(prefs.getInt("seconds", 0)) }

    // Timer state
    var isRunning by remember { mutableStateOf(TimerService.isRunning) }
    var isPaused by remember { mutableStateOf(TimerService.isPaused) }
    var remainingMillis by remember { mutableLongStateOf(TimerService.remainingMillis) }

    // Calculate display values
    val displayHours: Int
    val displayMinutes: Int
    val displaySeconds: Int

    if (isRunning || isPaused) {
        val totalSec = remainingMillis / 1000
        displayHours = (totalSec / 3600).toInt()
        displayMinutes = ((totalSec % 3600) / 60).toInt()
        displaySeconds = (totalSec % 60).toInt()
    } else {
        displayHours = hours
        displayMinutes = minutes
        displaySeconds = seconds
    }

    // Calculate total duration and progress for the ring
    val totalDurationMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
    val progress = if (isRunning && totalDurationMillis > 0) {
        remainingMillis.toFloat() / totalDurationMillis.toFloat()
    } else if (isPaused && totalDurationMillis > 0) {
        remainingMillis.toFloat() / totalDurationMillis.toFloat()
    } else {
        1f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )

    // Register listeners
    DisposableEffect(Unit) {
        TimerService.onTickListener = { millis ->
            remainingMillis = millis
        }
        TimerService.onStateChangeListener = { running, paused ->
            isRunning = running
            isPaused = paused
            if (!running && !paused) {
                remainingMillis = 0
            }
        }
        TimerService.onFinishListener = {
            isRunning = false
            isPaused = false
            remainingMillis = 0
        }
        // Sync with current service state
        isRunning = TimerService.isRunning
        isPaused = TimerService.isPaused
        if (isRunning || isPaused) {
            remainingMillis = TimerService.remainingMillis
        }

        onDispose {
            TimerService.onTickListener = null
            TimerService.onStateChangeListener = null
            TimerService.onFinishListener = null
        }
    }

    // Background with subtle gradient
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF12122A),
                        Color(0xFF0A0A1F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Floating ambient particles animation
        val infiniteTransition = rememberInfiniteTransition(label = "ambient")
        val particle1 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particle1"
        )
        val particle2 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(15000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particle2"
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = SleepTextSecondary,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Timer ring + display
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .drawBehind {
                        val strokeWidth = 6.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)

                        // Background ring
                        drawCircle(
                            color = Color(0xFF252542),
                            radius = radius,
                            center = center,
                            style = Stroke(width = strokeWidth)
                        )

                        // Progress ring
                        if (isRunning || isPaused) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        SleepPrimary,
                                        SleepSecondary,
                                        SleepPrimary
                                    )
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth)
                            )
                        }

                        // Floating particles
                        val p1Rad = Math.toRadians(particle1.toDouble())
                        val p1x = center.x + (radius + 20) * cos(p1Rad).toFloat()
                        val p1y = center.y + (radius + 20) * sin(p1Rad).toFloat()
                        drawCircle(
                            color = SleepPrimary.copy(alpha = 0.3f),
                            radius = 4.dp.toPx(),
                            center = Offset(p1x, p1y)
                        )

                        val p2Rad = Math.toRadians(particle2.toDouble() + 180)
                        val p2x = center.x + (radius + 15) * cos(p2Rad).toFloat()
                        val p2y = center.y + (radius + 15) * sin(p2Rad).toFloat()
                        drawCircle(
                            color = SleepSecondary.copy(alpha = 0.25f),
                            radius = 3.dp.toPx(),
                            center = Offset(p2x, p2y)
                        )
                    }
            ) {
                if (isRunning || isPaused) {
                    // Countdown display
                    Text(
                        text = String.format("%02d:%02d:%02d", displayHours, displayMinutes, displaySeconds),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Thin,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp
                    )
                } else {
                    // Editable time picker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TimePickerUnit(
                            value = hours,
                            onValueChange = { hours = it },
                            label = "h",
                            maxValue = 23
                        )
                        Text(
                            text = ":",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Thin,
                            color = SleepTextDim,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        TimePickerUnit(
                            value = minutes,
                            onValueChange = { minutes = it },
                            label = "m",
                            maxValue = 59
                        )
                        Text(
                            text = ":",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Thin,
                            color = SleepTextDim,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        TimePickerUnit(
                            value = seconds,
                            onValueChange = { seconds = it },
                            label = "s",
                            maxValue = 59
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Control buttons
            if (!isRunning && !isPaused) {
                // START
                Button(
                    onClick = {
                        val durationMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
                        if (durationMillis > 0) {
                            // Save as default
                            prefs.edit()
                                .putInt("hours", hours)
                                .putInt("minutes", minutes)
                                .putInt("seconds", seconds)
                                .apply()

                            val intent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_START
                                putExtra(TimerService.EXTRA_DURATION, durationMillis)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }

                            remainingMillis = durationMillis
                        }
                    },
                    modifier = Modifier
                        .width(200.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SleepPrimary
                    )
                ) {
                    Text(
                        text = "START",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp
                    )
                }
            } else {
                // PAUSE / RESUME + STOP
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause / Resume
                    Button(
                        onClick = {
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = if (isPaused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE
                            }
                            context.startService(intent)
                        },
                        modifier = Modifier
                            .width(140.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) SleepSecondary else SleepPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPaused) R.drawable.ic_play else R.drawable.ic_pause
                            ),
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPaused) "RESUME" else "PAUSE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        )
                    }

                    // Stop
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_STOP
                            }
                            context.startService(intent)
                        },
                        modifier = Modifier
                            .width(120.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SleepAccentPink
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stop),
                            contentDescription = "Stop",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STOP",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reset button
            AnimatedVisibility(
                visible = !isRunning && !isPaused,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedButton(
                    onClick = {
                        hours = prefs.getInt("hours", 0)
                        minutes = prefs.getInt("minutes", 30)
                        seconds = prefs.getInt("seconds", 0)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SleepTextSecondary
                    )
                ) {
                    Text(
                        text = "RESET TO DEFAULT",
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TimePickerUnit(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    maxValue: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Up arrow
        IconButton(
            onClick = {
                onValueChange(if (value >= maxValue) 0 else value + 1)
            },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF252542).copy(alpha = 0.5f))
        ) {
            Text(
                text = "▲",
                color = SleepPrimary,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Value
        Text(
            text = String.format("%02d", value),
            fontSize = 44.sp,
            fontWeight = FontWeight.Thin,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Label
        Text(
            text = label,
            fontSize = 12.sp,
            color = SleepTextDim,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Down arrow
        IconButton(
            onClick = {
                onValueChange(if (value <= 0) maxValue else value - 1)
            },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF252542).copy(alpha = 0.5f))
        ) {
            Text(
                text = "▼",
                color = SleepPrimary,
                fontSize = 16.sp
            )
        }
    }
}