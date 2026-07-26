package com.example.sample1

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val albumId: Long
)

data class AudioAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val trackCount: Int
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredTracks: StateFlow<List<AudioTrack>> = combine(_tracks, _searchQuery) { tracks, query ->
        if (query.isBlank()) tracks
        else tracks.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _albums = MutableStateFlow<List<AudioAlbum>>(emptyList())
    val albums: StateFlow<List<AudioAlbum>> = _albums

    val selectedTrackForPlaylist = MutableStateFlow<AudioTrack?>(null)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadMedia() {
        viewModelScope.launch {
            _tracks.value = fetchTracks()
            _albums.value = fetchAlbums()
        }
    }

    fun getTracksByAlbum(albumId: Long): List<AudioTrack> {
        return _tracks.value.filter { it.albumId == albumId }
    }

    private suspend fun fetchTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val trackList = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        // Some devices don't set IS_MUSIC correctly. Checking duration > 0 is more reliable for playable files.
        val selection = "${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        getApplication<Application>().contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()

                trackList.add(AudioTrack(id, title, artist, album, uri, albumId))
            }
        }
        trackList
    }

    private suspend fun fetchAlbums(): List<AudioAlbum> = withContext(Dispatchers.IO) {
        val albumList = mutableListOf<AudioAlbum>()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )

        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"

        getApplication<Application>().contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val countColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(albumColumn) ?: "Unknown Album"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val count = cursor.getInt(countColumn)

                albumList.add(AudioAlbum(id, title, artist, count))
            }
        }
        albumList
    }
}
