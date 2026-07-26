package com.example.sample1

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {

    private val db by lazy {
        Room.databaseBuilder(
            application.applicationContext,
            AppDatabase::class.java, "playlist_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    private val playlistDao by lazy { db.playlistDao() }
    private val alarmScheduler = AlarmScheduler(application)

    val playlists: StateFlow<List<PlaylistWithSongs>> = playlistDao.getAllPlaylistsWithSongs()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPlaylist(name: String, songs: List<Song>, alarmTime: Long, repeatMode: Int = 0, daysOfWeek: String = "", scheduledDate: Long = 0L, isAutoDelete: Boolean = false) {
        viewModelScope.launch {
            try {
                val playlist = Playlist(
                    name = name, 
                    alarmTime = alarmTime, 
                    scheduledDate = scheduledDate,
                    isEnabled = true,
                    isAutoDelete = isAutoDelete,
                    repeatMode = repeatMode,
                    daysOfWeek = daysOfWeek
                )
                val playlistId = playlistDao.insertPlaylist(playlist).toInt()
                val songsWithId = songs.map { it.copy(playlistId = playlistId) }
                playlistDao.insertSongs(songsWithId)
                
                // For scheduling, we need the full object
                val fullPlaylist = PlaylistWithSongs(playlist.copy(id = playlistId), songsWithId)
                alarmScheduler.schedule(fullPlaylist)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updatePlaylist(playlistWithSongs: PlaylistWithSongs) {
        viewModelScope.launch {
            try {
                playlistDao.updatePlaylist(playlistWithSongs.playlist)
                playlistDao.deleteSongsForPlaylist(playlistWithSongs.playlist.id)
                playlistDao.insertSongs(playlistWithSongs.songs)
                
                if (playlistWithSongs.playlist.isEnabled) {
                    alarmScheduler.schedule(playlistWithSongs)
                } else {
                    alarmScheduler.cancel(playlistWithSongs)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deletePlaylist(playlistWithSongs: PlaylistWithSongs) {
        viewModelScope.launch {
            try {
                alarmScheduler.cancel(playlistWithSongs)
                playlistDao.deleteSongsForPlaylist(playlistWithSongs.playlist.id)
                playlistDao.deletePlaylist(playlistWithSongs.playlist)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlaylist(playlistWithSongs: PlaylistWithSongs) {
        val updated = playlistWithSongs.copy(
            playlist = playlistWithSongs.playlist.copy(isEnabled = !playlistWithSongs.playlist.isEnabled)
        )
        updatePlaylist(updated)
    }

    var pendingTrack: Song? = null

    fun addTrackToPlaylist(playlistWithSongs: PlaylistWithSongs, track: AudioTrack) {
        val song = Song(
            uri = track.uri,
            title = track.title,
            playlistId = playlistWithSongs.playlist.id
        )
        updatePlaylist(playlistWithSongs.copy(songs = playlistWithSongs.songs + song))
    }
}