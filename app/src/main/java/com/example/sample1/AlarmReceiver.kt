package com.example.sample1

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.TIME_SET" ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            
            // Reschedule all active alarms
            val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "playlist_db").build()
            val scheduler = AlarmScheduler(context)
            GlobalScope.launch {
                db.playlistDao().getAllPlaylistsWithSongsOnce().forEach { item ->
                    if (item.playlist.isEnabled) {
                        scheduler.schedule(item)
                    }
                }
                db.close()
            }
            return
        }

        val songUris = intent.getStringArrayExtra("songUris")
        val songTitles = intent.getStringArrayExtra("songTitles")
        val playlistName = intent.getStringExtra("playlistName") ?: "Alarm"
        val playlistId = intent.getIntExtra("playlistId", -1)
        val repeatMode = intent.getIntExtra("repeatMode", 0)
        val daysOfWeek = intent.getStringExtra("daysOfWeek") ?: ""
        val alarmTime = intent.getLongExtra("alarmTime", 0L)
        val isAutoDelete = intent.getBooleanExtra("isAutoDelete", false)
        
        Log.d("AlarmReceiver", "Alarm received for $playlistName (Repeat: $repeatMode, Auto-Delete: $isAutoDelete)")

        // Handle auto-delete: Only delete if it's NOT a repeating playlist
        if (isAutoDelete && repeatMode == 0 && playlistId != -1) {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, "playlist_db"
            ).build()
            
            GlobalScope.launch {
                db.playlistDao().deletePlaylistById(playlistId)
                db.playlistDao().deleteSongsForPlaylist(playlistId)
                db.close()
            }
        } else if (repeatMode > 0) {
            // Reschedule if repeating and NOT auto-deleting
            val scheduler = AlarmScheduler(context)
            val songs = if (songUris != null && songTitles != null) {
                songUris.indices.map { i -> Song(uri = songUris[i], title = songTitles[i], playlistId = playlistId) }
            } else emptyList()
            
            val playlist = Playlist(
                id = playlistId,
                name = playlistName,
                alarmTime = alarmTime,
                isEnabled = true,
                isAutoDelete = false, // If we reached here, it's false
                repeatMode = repeatMode,
                daysOfWeek = daysOfWeek
            )
            scheduler.schedule(PlaylistWithSongs(playlist, songs))
        }

        if (songUris != null && songUris.isNotEmpty()) {
            val serviceIntent = Intent(context, MusicService::class.java).apply {
                putExtra("songUris", songUris)
                putExtra("songTitles", songTitles)
                putExtra("playlistName", playlistName)
                action = "START_FROM_ALARM"
                
                val clip = ClipData.newRawUri("Songs", Uri.parse(songUris[0]))
                for (i in 1 until songUris.size) {
                    clip.addItem(ClipData.Item(Uri.parse(songUris[i])))
                }
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Always use startForegroundService for Alarms on Android 8+
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            Log.w("AlarmReceiver", "No songs to play for alarm: $playlistName")
        }
    }
}
