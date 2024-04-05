package com.chaquo.myapplication

import android.content.Context
import androidx.room.Room
import com.chaquo.myapplication.db.AppDatabase

class Helper {
    fun getLocalUserName(applicationContext: Context): String {
        val db : AppDatabase = getDB(applicationContext)
        val user = db.userDao().findActive()

        if (user != null) {
            return user.username.toString()
        }

        return "x"
    }

    fun getDB(applicationContext: Context): AppDatabase {
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
    }

    fun getLocalUserNum(applicationContext: Context): String {
        val db : AppDatabase = getDB(applicationContext)
        val user = db.userDao().findActive()

        if (user != null) {
            return user.uid.toString()
        }

        return "x"
    }

}