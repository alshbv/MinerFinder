package com.chaquo.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import com.chaquo.myapplication.databinding.ActivityCameraBinding
import com.chaquo.myapplication.databinding.ActivityMessageBinding
import java.util.concurrent.ExecutorService


class Message : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMessageBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMessageBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)



    }
}