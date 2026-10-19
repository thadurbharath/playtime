package com.example.sample1

import android.content.BroadcastReceiver
import android.content.IntentFilter
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

import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import android.net.wifi.WifiManager
import android.os.PowerManager

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer
    private var currentPlaylistName: String = "PlayTime"
    private var isFromAlarmSession: Boolean = false
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fadeJob: Job? = null
    private var sleepJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var pendingLinkOnUnlock: String? = null

    private val screenUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                pendingLinkOnUnlock?.let { uri ->
                    triggerExternalLink(uri)
                    pendingLinkOnUnlock = null
                }
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "volume_boost") {
            applyAudioSettings()
        } else if (key == "sleep_timer") {
            updateSleepTimer()
        }
    }

    private fun applyAudioSettings() {
        val boost = prefs.getFloat("volume_boost", 1.0f)
        if (fadeJob?.isActive != true) {
            exoPlayer.volume = boost
        }
    }

    private fun isExternalPlatform(uri: String): Boolean {
        return uri.contains("youtube.com") || 
               uri.contains("youtu.be") || 
               uri.contains("spotify.com") ||
               uri.contains("music.apple.com")
    }

    private fun triggerExternalLink(uri: String) {
        if (uri.startsWith("http") && isExternalPlatform(uri)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = powerManager.isInteractive

            if (!isScreenOn) {
                // If screen is off, don't trigger yet, wait for unlock
                pendingLinkOnUnlock = uri
                Log.d("MusicService", "Screen is OFF. Delaying link trigger until unlock: $uri")
                return
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.d("MusicService", "Triggered external link: $uri")
            } catch (e: Exception) {
                Log.e("MusicService", "Failed to open external link", e)
            }
        }
    }

    private fun startFadeIn() {
        if (!prefs.getBoolean("fade_in", true)) return
        
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            val targetVolume = prefs.getFloat("volume_boost", 1.0f)
            val durationMs = 30000L
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
        const val ACTION_OPEN_LINK = "com.example.sample1.OPEN_LINK"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("playtime_settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(screenUnlockReceiver, filter)

        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PlayTime:WiFiLock")

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("PlayTime/1.1.4 (Android)")
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.accept(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    )
                }
                
                override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
                    super.onPostConnect(session, controller)
                    updateNotification(isFromAlarmSession, currentPlaylistName)
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    // Force prepare if trying to play or skip from idle/error state (links)
                    if (playerCommand == Player.COMMAND_PLAY_PAUSE || 
                        playerCommand == Player.COMMAND_SEEK_TO_NEXT || 
                        playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS) {
                        if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playerError != null) {
                            exoPlayer.prepare()
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            })
            .build()
            
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                
                if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                    if (wifiLock?.isHeld == false) wifiLock?.acquire()
                } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    if (wifiLock?.isHeld == true) wifiLock?.release()
                }
                
                updateNotification(isFromAlarmSession, currentPlaylistName)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (!playing && exoPlayer.playbackState != Player.STATE_BUFFERING) {
                    // When paused, stop foreground to make notification swipeable, but DON'T stopSelf
                    // so the app can still resume playback.
                    stopForeground(STOP_FOREGROUND_DETACH)
                    updateNotification(isFromAlarmSession, currentPlaylistName)
                } else {
                    updateNotification(isFromAlarmSession, currentPlaylistName)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val currentUri = exoPlayer.currentMediaItem?.mediaId ?: ""
                if (isExternalPlatform(currentUri)) {
                    triggerExternalLink(currentUri)
                }
                updateNotification(isFromAlarmSession, currentPlaylistName) 
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                updateNotification(isFromAlarmSession, currentPlaylistName)
            }

            override fun onPlayerError(error: PlaybackException) {
                updateNotification(isFromAlarmSession, currentPlaylistName)
            }
        })
            
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Used for background music playback and scheduled timers"
                setSound(null, null)
                enableVibration(true)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }

        when (intent.action) {
            "STOP" -> {
                exoPlayer.stop(); exoPlayer.clearMediaItems()
                if (wifiLock?.isHeld == true) wifiLock?.release()
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                return START_NOT_STICKY
            }
            "PAUSE" -> {
                exoPlayer.pause()
                if (wifiLock?.isHeld == true) wifiLock?.release()
                updateNotification(isFromAlarmSession, currentPlaylistName)
                return START_NOT_STICKY
            }
            "PLAY" -> {
                if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playerError != null) {
                    exoPlayer.prepare()
                }
                exoPlayer.play()
                updateNotification(isFromAlarmSession, currentPlaylistName)
                return START_NOT_STICKY
            }
            ACTION_OPEN_LINK -> {
                intent.getStringExtra("link")?.let { triggerExternalLink(it) }
                return START_NOT_STICKY
            }
        }

        isFromAlarmSession = intent.action == "START_FROM_ALARM"
        currentPlaylistName = intent.getStringExtra("playlistName") ?: "PlayTime"
        updateNotification(isFromAlarmSession, currentPlaylistName)

        if (isFromAlarmSession) exoPlayer.repeatMode = Player.REPEAT_MODE_OFF

        val uris = intent.getStringArrayExtra("songUris")
        val titles = intent.getStringArrayExtra("songTitles")
        val startIndex = intent.getIntExtra("startIndex", 0)
        
        if (uris != null && titles != null && uris.isNotEmpty()) {
            exoPlayer.stop(); exoPlayer.clearMediaItems()
            for (i in uris.indices) {
                val isExternal = isExternalPlatform(uris[i])
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(titles[i])
                    .setArtist(if (isFromAlarmSession) "Playlist: $currentPlaylistName" else "PlayTime")
                    .setAlbumTitle(if (isExternal) "Platform Link (Tap Launch)" else currentPlaylistName)
                    .build()
                exoPlayer.addMediaItem(MediaItem.Builder().setMediaId(uris[i]).setUri(Uri.parse(uris[i])).setMediaMetadata(mediaMetadata).build())
            }
            exoPlayer.seekTo(startIndex, 0L); exoPlayer.prepare(); exoPlayer.playWhenReady = true; exoPlayer.play()
            if (isFromAlarmSession) startFadeIn()
            updateSleepTimer()
            updateNotification(isFromAlarmSession, currentPlaylistName)
        } else if (isFromAlarmSession) {
            stopSelf()
        } else if (exoPlayer.mediaItemCount > 0) {
            updateNotification(isFromAlarmSession, currentPlaylistName)
        } else if (!exoPlayer.isPlaying) {
            stopSelf()
        }
        
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    @UnstableApi
    private fun updateNotification(isFromAlarm: Boolean, playlistName: String) {
        val session = mediaSession ?: return
        val currentItem = exoPlayer.currentMediaItem
        val currentUri = currentItem?.mediaId ?: ""
        val isExternal = isExternalPlatform(currentUri)

        val intentToOpen = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intentToOpen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isFromAlarm) "Scheduled Playlist: $playlistName" else "PlayTime")
            .setContentText(currentItem?.mediaMetadata?.title ?: "Enjoy your music")
            .setSmallIcon(R.drawable.ic_play_notification)
            .setOngoing(exoPlayer.isPlaying || isExternal)
            .setCategory(if (isFromAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))

        if (isExternal) {
            val openIntent = Intent(this, MusicService::class.java).apply { action = ACTION_OPEN_LINK; putExtra("link", currentUri) }
            val openPendingIntent = PendingIntent.getService(this, 2, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(R.drawable.ic_play_notification, "Launch App", openPendingIntent)
        }

        if (isFromAlarm) {
            val alarmIntent = Intent(this, AlarmActivity::class.java).apply { putExtra("playlistName", playlistName); putExtra("currentUri", currentUri); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            val alarmPendingIntent = PendingIntent.getActivity(this, 1, alarmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setFullScreenIntent(alarmPendingIntent, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, builder.build())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onDestroy() { 
        serviceScope.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        try { unregisterReceiver(screenUnlockReceiver) } catch (e: Exception) {}
        if (wifiLock?.isHeld == true) wifiLock?.release()
        mediaSession?.run { player.release(); release(); mediaSession = null }; super.onDestroy() 
    }
    override fun onTaskRemoved(rootIntent: Intent?) { exoPlayer.stop(); exoPlayer.clearMediaItems(); if (wifiLock?.isHeld == true) wifiLock?.release(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
}
