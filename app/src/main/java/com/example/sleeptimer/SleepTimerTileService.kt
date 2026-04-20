package com.example.sleeptimer


import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class SleepTimerTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val prefs = getSharedPreferences("sleep_timer_prefs", Context.MODE_PRIVATE)
        val hours = prefs.getInt("hours", 0)
        val minutes = prefs.getInt("minutes", 30)
        val seconds = prefs.getInt("seconds", 0)
        val durationMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L

        if (TimerService.isRunning) {
            // If timer is already running, stop it
            val stopIntent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            // Start the timer with saved defaults
            if (durationMillis > 0) {
                val serviceIntent = Intent(this, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_DURATION, durationMillis)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }


        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.label = "Sleep Timer"
        tile.updateTile()
    }
}
