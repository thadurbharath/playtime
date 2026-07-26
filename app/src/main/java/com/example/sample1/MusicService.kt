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
import android.content.SharedPreferences
import com.example.sample1.R
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private var currentPlaylistName: String = "PlayTime"
    private var isFromAlarmSession: Boolean = false
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fadeJob: Job? = null
    private var sleepJob: Job? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "volume_boost") {
            applyAudioSettings()
        } else if (key == "sleep_timer") {
            updateSleepTimer()
        }
    }

    private fun applyAudioSettings() {
        val boost = prefs.getFloat("volume_boost", 1.0f)
        // If not currently fading, apply the boost directly
        if (fadeJob?.isActive != true) {
            exoPlayer.volume = boost
        }
    }

    private fun startFadeIn() {
        if (!prefs.getBoolean("fade_in", true)) return
        
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            val targetVolume = prefs.getFloat("volume_boost", 1.0f)
            val durationMs = 30000L // 30 seconds fade
            val intervalMs = 500L
            val steps = durationMs / intervalMs
            val volumeStep = targetVolume / steps
            
            exoPlayer.volume = 0f
            for (i in 1..steps) {
                delay(intervalMs)
                exoPlayer.volume = (volumeStep * i).coerceAtMost(targetVolume)
            }
        }
    }

    private fun updateSleepTimer() {
        val minutes = prefs.getInt("sleep_timer", 0)
        sleepJob?.cancel()
        if (minutes > 0) {
            sleepJob = serviceScope.launch {
                delay(minutes * 60000L)
                exoPlayer.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "music_service_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("playtime_settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false // Disable automatic audio focus handling for non-media usage compatibility
            )
            .setWakeMode(C.WAKE_MODE_NONE)
            .build()
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
            
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    Log.d("MusicService", "Playlist finished, stopping service.")
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                updateNotification(isFromAlarmSession, currentPlaylistName)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && exoPlayer.playbackState != Player.STATE_BUFFERING) {
                    // If player stops playing, remove foreground and stop service to save battery
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification(isFromAlarmSession, currentPlaylistName)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // Update notification when track changes
                updateNotification(isFromAlarmSession, currentPlaylistName) 
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicService", "ExoPlayer Error: ${error.message}", error)
            }
        })
            
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used for background music playback and alarms"
                setSound(null, null)
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "onStartCommand action: ${intent?.action}")
        
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == "STOP") {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == "PAUSE") {
            exoPlayer.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == "PLAY") {
            exoPlayer.play()
            updateNotification(isFromAlarmSession, currentPlaylistName)
            return START_NOT_STICKY
        }

        // Always show a notification if started by alarm or if playing
        isFromAlarmSession = intent.action == "START_FROM_ALARM"
        currentPlaylistName = intent.getStringExtra("playlistName") ?: "PlayTime"
        
        // Show notification IMMEDIATELY to satisfy foreground service requirements
        updateNotification(isFromAlarmSession, currentPlaylistName)

        if (isFromAlarmSession) {
            // For scheduled alarms, we ensure repeat mode is OFF so it hits STATE_ENDED eventually
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        }

        val uris = intent.getStringArrayExtra("songUris")
        val titles = intent.getStringArrayExtra("songTitles")
        val startIndex = intent.getIntExtra("startIndex", 0)
        
        if (uris != null && titles != null && uris.isNotEmpty()) {
            Log.d("MusicService", "Loading ${uris.size} songs starting at $startIndex")
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            for (i in uris.indices) {
                try {
                    val mediaMetadata = MediaMetadata.Builder()
                        .setTitle(titles[i])
                        .setDisplayTitle(titles[i])
                        .setArtist(if (isFromAlarmSession) "Playlist: $currentPlaylistName" else "PlayTime")
                        .setAlbumTitle(currentPlaylistName)
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
            
            // On Redmi/MIUI, sometimes the system blocks play() if called too quickly 
            // after startForeground. A small delay or explicit playWhenReady can help.
            exoPlayer.playWhenReady = true
            exoPlayer.play()
            
            if (isFromAlarmSession) {
                startFadeIn()
            }
            updateSleepTimer()
            
            // Update notification again to show the title of the first song
            updateNotification(isFromAlarmSession, currentPlaylistName)
        } else if (isFromAlarmSession) {
            // Started from alarm but no songs? Stop.
            Log.w("MusicService", "Started from alarm but no songs provided.")
            stopSelf()
        } else if (exoPlayer.mediaItemCount > 0) {
            // Already have songs (maybe just a simple start), update notification
            updateNotification(isFromAlarmSession, currentPlaylistName)
        } else if (!exoPlayer.isPlaying) {
            // No songs and not playing anything, stop.
            stopSelf()
        }
        
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    @UnstableApi
    private fun updateNotification(isFromAlarm: Boolean, playlistName: String) {
        val session = mediaSession ?: return
        
        val intentToOpen = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intentToOpen, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isFromAlarm) "Scheduled Playlist: $playlistName" else "PlayTime")
            .setContentText(exoPlayer.currentMediaItem?.mediaMetadata?.title ?: "Enjoy your music")
            .setSmallIcon(R.drawable.ic_play_notification)
            .setOngoing(exoPlayer.isPlaying)
            .setCategory(if (isFromAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))

        if (isFromAlarm) {
            val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
                putExtra("playlistName", playlistName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val alarmPendingIntent = PendingIntent.getActivity(
                this, 1, alarmIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setFullScreenIntent(alarmPendingIntent, true)
        }

        val notification = builder.build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}