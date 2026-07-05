package com.example.sample1

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val alarmTime: Long = 0L,
    val isEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0: Once, 1: Daily, 2: Specific Days
    val daysOfWeek: String = "" // Comma-separated days like "1,2,3" (1=Sun, 7=Sat)
)

@Entity
data class Song(
    @PrimaryKey(autoGenerate = true)
    val songId: Int = 0,
    val playlistId: Int,
    val uri: String,
    val title: String
)

data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val songs: List<Song>
)