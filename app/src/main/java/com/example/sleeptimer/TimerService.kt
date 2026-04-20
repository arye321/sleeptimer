package com.example.sleeptimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "sleep_timer_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.example.sleeptimer.ACTION_START"
        const val ACTION_PAUSE = "com.example.sleeptimer.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.sleeptimer.ACTION_RESUME"
        const val ACTION_STOP = "com.example.sleeptimer.ACTION_STOP"
        const val ACTION_TICK = "com.example.sleeptimer.ACTION_TICK"
        const val ACTION_FINISHED = "com.example.sleeptimer.ACTION_FINISHED"

        const val EXTRA_DURATION = "extra_duration"

        var isRunning = false
            private set
        var isPaused = false
            private set
        var remainingMillis: Long = 0L
            private set

        // Listeners for UI updates
        var onTickListener: ((Long) -> Unit)? = null
        var onFinishListener: (() -> Unit)? = null
        var onStateChangeListener: ((Boolean, Boolean) -> Unit)? = null // (isRunning, isPaused)
    }

    private var countDownTimer: CountDownTimer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalVolume: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
                if (duration > 0) {
                    startTimer(duration)
                }
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    private fun startTimer(durationMillis: Long) {

        // Acquire wake lock to keep timer running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepTimer::TimerWakeLock"
        ).apply {
            acquire(durationMillis + 60000) // duration + 1 min buffer
        }

        remainingMillis = durationMillis
        isRunning = true
        isPaused = false

        // Save original volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        startForeground(NOTIFICATION_ID, buildNotification())

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                onTickListener?.invoke(millisUntilFinished)
                updateNotification()
            }

            override fun onFinish() {
                remainingMillis = 0
                onTickListener?.invoke(0)
                performSleepActions()
            }
        }.start()

        onStateChangeListener?.invoke(true, false)
        updateTileState(this)
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isPaused = true
        onStateChangeListener?.invoke(true, true)
        updateNotification()
    }

    private fun resumeTimer() {
        isPaused = false
        onStateChangeListener?.invoke(true, false)

        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                onTickListener?.invoke(millisUntilFinished)
                updateNotification()
            }

            override fun onFinish() {
                remainingMillis = 0
                onTickListener?.invoke(0)
                performSleepActions()
            }
        }.start()

        updateNotification()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
        isPaused = false
        remainingMillis = 0

        releaseWakeLock()
        onStateChangeListener?.invoke(false, false)
        updateTileState(this)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

    }

    private fun performSleepActions() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Gradually lower volume over ~5 seconds
        val steps = 10
        val delayPerStep = 500L // 500ms per step = 5 seconds total
        val volumeDecrement = if (steps > 0) currentVolume.toFloat() / steps else 0f

        Thread {
            for (i in 1..steps) {
                val newVolume = (currentVolume - (volumeDecrement * i)).toInt().coerceAtLeast(0)
                try {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        newVolume,
                        0
                    )
                } catch (_: Exception) { }
                Thread.sleep(delayPerStep)
            }

            // Ensure volume is 0
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } catch (_: Exception) { }

            // Try to pause media playback by stealing audio focus
            try {
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            } catch (_: Exception) { }

            // Wait a moment for media to actually stop
            Thread.sleep(500)

            // Restore volume back to what it was before the timer
            try {
                val restoreVolume = if (originalVolume >= 0) originalVolume else currentVolume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0)
            } catch (_: Exception) { }

            // Minimize all apps - go to home screen
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            } catch (_: Exception) { }

            // Small delay before locking so the home screen transition completes
            Thread.sleep(300)

            // Try to lock the screen
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                dpm?.lockNow()
            } catch (_: SecurityException) {
                // Device admin not enabled - that's okay
            } catch (_: Exception) { }

            // Notify finish
            onFinishListener?.invoke()

            // Clean up
            isRunning = false
            isPaused = false
            releaseWakeLock()
            onStateChangeListener?.invoke(false, false)
            updateTileState(this@TimerService)

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.start()
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) { }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sleep Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows sleep timer countdown"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val timeText = formatTime(remainingMillis)

        // Content intent - open the app
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Timer")
            .setContentText(timeText)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (isPaused) {
            // Show Resume button
            val resumeIntent = PendingIntent.getService(
                this, 1,
                Intent(this, TimerService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_play, "Resume", resumeIntent)

            // Show Stop button
            val stopIntent = PendingIntent.getService(
                this, 3,
                Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_stop, "Stop", stopIntent)
        } else {
            // Show Pause button
            val pauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_pause, "Pause", pauseIntent)

            // Show Stop button
            val stopIntent = PendingIntent.getService(
                this, 3,
                Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_stop, "Stop", stopIntent)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun updateTileState(context: Context) {
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, SleepTimerTileService::class.java)
            )
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        releaseWakeLock()
        isRunning = false
        isPaused = false
        updateTileState(this)
        super.onDestroy()
    }
}
