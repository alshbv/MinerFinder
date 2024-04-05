package com.chaquo.myapplication.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [User::class, StoredImageData::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun storedImageDao(): StoredImageDao
}