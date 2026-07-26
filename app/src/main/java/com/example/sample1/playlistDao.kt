package com.example.sample1

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM Playlist WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Int)

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Transaction
    @Query("SELECT * FROM Playlist")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>

    @Transaction
    @Query("SELECT * FROM Playlist")
    suspend fun getAllPlaylistsWithSongsOnce(): List<PlaylistWithSongs>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("DELETE FROM Song WHERE playlistId = :playlistId")
    suspend fun deleteSongsForPlaylist(playlistId: Int)
}