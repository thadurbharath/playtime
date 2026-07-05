package com.example.sample1

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Playlist::class, Song::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
}