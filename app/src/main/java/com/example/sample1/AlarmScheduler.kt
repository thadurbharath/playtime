package com.example.sample1

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(playlistWithSongs: PlaylistWithSongs) {
        val playlist = playlistWithSongs.playlist
        
        // Calculate the next trigger time based on repeat mode
        val now = Calendar.getInstance()
        val scheduledTime = Calendar.getInstance().apply { 
            timeInMillis = playlist.alarmTime 
            
            // Set to current date initially
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            
            // If the set time is in the past for today, move it forward
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Specific day logic
        if (playlist.repeatMode == 2 && playlist.daysOfWeek.isNotEmpty()) {
            val enabledDays = playlist.daysOfWeek.split(",").map { it.toInt() }
            // Check next 7 days to find the next matching day
            var found = false
            for (i in 0..7) {
                if (enabledDays.contains(scheduledTime.get(Calendar.DAY_OF_WEEK))) {
                    found = true
                    break
                }
                scheduledTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (!found) {
                Log.w("AlarmScheduler", "No valid days found for playlist ${playlist.name}")
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val songUris = playlistWithSongs.songs.map { it.uri }.toTypedArray()
        val songTitles = playlistWithSongs.songs.map { it.title }.toTypedArray()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("songUris", songUris)
            putExtra("songTitles", songTitles)
            putExtra("playlistName", playlist.name)
            putExtra("playlistId", playlist.id)
            putExtra("repeatMode", playlist.repeatMode)
            putExtra("daysOfWeek", playlist.daysOfWeek)
            putExtra("alarmTime", playlist.alarmTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            playlist.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Using setAlarmClock for maximum reliability and visibility
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, playlist.id, showIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmClockInfo = AlarmManager.AlarmClockInfo(scheduledTime.timeInMillis, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        
        Log.d("AlarmScheduler", "Scheduled Alarm for ${playlist.name} at ${scheduledTime.time}")
    }

    fun cancel(playlistWithSongs: PlaylistWithSongs) {
        val playlist = playlistWithSongs.playlist
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            playlist.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("AlarmScheduler", "Canceled alarm for ${playlist.name}")
    }
}