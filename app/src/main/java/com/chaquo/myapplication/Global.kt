package com.chaquo.myapplication
import android.app.Application

class Global : Application(){
    //val found_eid = mutableListOf<String>()

    companion object {
        lateinit var instance: Global
            private set
    }

    val found_eid = mutableListOf<String>()

    var did_connect = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

}