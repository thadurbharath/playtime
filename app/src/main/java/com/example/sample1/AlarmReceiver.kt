package com.example.sample1

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val songUris = intent.getStringArrayExtra("songUris")
        val songTitles = intent.getStringArrayExtra("songTitles")
        val playlistName = intent.getStringExtra("playlistName") ?: "Alarm"
        val playlistId = intent.getIntExtra("playlistId", -1)
        val repeatMode = intent.getIntExtra("repeatMode", 0)
        val daysOfWeek = intent.getStringExtra("daysOfWeek") ?: ""
        val alarmTime = intent.getLongExtra("alarmTime", 0L)
        
        Log.d("AlarmReceiver", "Alarm received for $playlistName (Repeat: $repeatMode)")

        // Reschedule if repeating
        if (repeatMode > 0) {
            val scheduler = AlarmScheduler(context)
            val songs = if (songUris != null && songTitles != null) {
                songUris.indices.map { i -> Song(uri = songUris[i], title = songTitles[i], playlistId = playlistId) }
            } else emptyList()
            
            val playlist = Playlist(
                id = playlistId,
                name = playlistName,
                alarmTime = alarmTime,
                isEnabled = true,
                repeatMode = repeatMode,
                daysOfWeek = daysOfWeek
            )
            scheduler.schedule(PlaylistWithSongs(playlist, songs))
        }

        val serviceIntent = Intent(context, MusicService::class.java).apply {
            putExtra("songUris", songUris)
            putExtra("songTitles", songTitles)
            putExtra("playlistName", playlistName)
            action = "START_FROM_ALARM"
            
            if (songUris != null && songUris.isNotEmpty()) {
                val clip = ClipData.newRawUri("Songs", Uri.parse(songUris[0]))
                for (i in 1 until songUris.size) {
                    clip.addItem(ClipData.Item(Uri.parse(songUris[i])))
                }
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        
        // Always use startForegroundService for Alarms on Android 8+
        ContextCompat.startForegroundService(context, serviceIntent)

        // Launch full-screen activity
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("playlistName", playlistName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(activityIntent)
    }
}