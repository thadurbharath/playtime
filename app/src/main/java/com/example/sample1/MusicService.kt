package com.example.sample1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer

    companion object {
        const val CHANNEL_ID = "music_service_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
            
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "onStartCommand action: ${intent?.action}")
        
        if (intent?.action == "STOP") {
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            stopForeground(STOP_FOREGROUND_REMOVE)
            return START_NOT_STICKY
        }

        if (intent?.action == "PAUSE") {
            exoPlayer.pause()
            return START_NOT_STICKY
        }

        if (intent?.action == "PLAY") {
            exoPlayer.play()
            return START_NOT_STICKY
        }

        // Always show a notification if started by alarm or if playing
        val isFromAlarm = intent?.action == "START_FROM_ALARM"
        val playlistName = intent?.getStringExtra("playlistName") ?: "My Melody"
        
        updateNotification(isFromAlarm, playlistName)

        val uris = intent?.getStringArrayExtra("songUris")
        val titles = intent?.getStringArrayExtra("songTitles")
        val startIndex = intent?.getIntExtra("startIndex", 0) ?: 0
        
        if (uris != null && titles != null && uris.isNotEmpty()) {
            Log.d("MusicService", "Loading ${uris.size} songs starting at $startIndex")
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            for (i in uris.indices) {
                try {
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(titles[i])
                        .setDisplayTitle(titles[i])
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(uris[i])
                        .setUri(Uri.parse(uris[i]))
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    exoPlayer.addMediaItem(mediaItem)
                } catch (e: Exception) {
                    Log.e("MusicService", "Failed to add media item: ${uris[i]}", e)
                }
            }
            exoPlayer.seekTo(startIndex, 0L)
            exoPlayer.prepare()
            exoPlayer.play()
        }
        
        return super.onStartCommand(intent, flags, startId)
    }

    @UnstableApi
    private fun updateNotification(isFromAlarm: Boolean, playlistName: String) {
        val intentToOpen = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intentToOpen, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isFromAlarm) "Alarm: $playlistName" else "Now Playing")
            .setContentText(if (isFromAlarm) "Playing your wake-up playlist" else exoPlayer.currentMediaItem?.mediaMetadata?.title ?: "Enjoy your music")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession!!))
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false || player?.mediaItemCount == 0) {
            stopSelf()
        }
    }
}