package com.chaquo.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Strategy
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random


class Pillar : AppCompatActivity() {
    private val TAG = "Connection"
    private val context: Context = this

    // reduce Helper() calls if neccessary
    private lateinit var localID: String



    private lateinit var stepCounter_Pillar: StepCounter
    private var currentPillar: String = ""
    var isAPillar = false
    var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StepCounter.MyBinder
            stepCounter_Pillar = binder.getService()
            isBound = true

            if (stepCounter_Pillar.advertisingID.isNotEmpty()) {
                Log.d("SAVEDSTATE", "HERE")
                val savedText = stepCounter_Pillar.advertisingID[0].toString()
                val pillarTextView: TextView = findViewById(R.id.PillarView)
                if(savedText[0].isUpperCase())
                {
                    pillarTextView.text = "Broadcasting as $savedText"
                }

                isAPillar = (stepCounter_Pillar.advertisingID.last() == 'P')
                Log.d("Binding-TAG", stepCounter_Pillar.advertisingID)

                val button1 = findViewById<Button>(R.id.buttonU)
                button1.performClick()
            }

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Handle disconnection
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pillar_location)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        val textView: TextView = findViewById(R.id.textView)
        val pillarTextView: TextView = findViewById(R.id.PillarView)
        val pillarConnectedTo: TextView = findViewById(R.id.PillarConnectedTo)
        val button1: Button = findViewById(R.id.buttonAdvAsPillar)
        val button2: Button = findViewById(R.id.buttonStopAsPillar)
        val buttonU: Button = findViewById(R.id.buttonU)
        val locationEntries: TextView = findViewById(R.id.lastEntries)

        val intent = Intent(this, StepCounter::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)



        // Set onClickListeners for the buttons (add functionality as needed)
        button1.setOnClickListener {
            // start advertising
            // for now, set out a list of pillars.
            val randomIndex = Random.nextInt(1, 26) // Generates a random number between 1 and 25
            val randomAlphabet = ('A'.toInt() + randomIndex).toChar().toString()

            stepCounter_Pillar.setPillar(randomAlphabet)
            isAPillar = true
            pillarTextView.text = "Broadcasting as $randomAlphabet"

        }

        button2.setOnClickListener {
            // stop advertising
            //stepCounter_Pillar.stopDiscovery()
            stepCounter_Pillar.setMiner()
            isAPillar = false
            pillarTextView.text = "Not advertising as a pillar"

            //stepCounter_Pillar.stopAdvertising()
            //stepCounter_Pillar.stopDiscovery()
        }

        buttonU.setOnClickListener {
            //Log.d("currentPillar", currentPillar)
            //Log.d("currentPillar - 2", stepCounter_Pillar.getPillarNew())
            currentPillar = stepCounter_Pillar.getPillarNew()
            if(isAPillar)
            {
                pillarConnectedTo.text = "Miners in range: " + stepCounter_Pillar.getMinerList().toString()
            }
            else
            {
                pillarConnectedTo.text = "Connected To: " + currentPillar
            }

            // Read data from file
            val fileName = "${Helper().getLocalUserName(applicationContext)}.json"
            val fileInputStream = openFileInput(fileName)
            val reader = BufferedReader(InputStreamReader(fileInputStream))
            val lines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val items = line!!.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                lines.addAll(items)
            }
            reader.close()

            val lastline = lines.last()
            val timestamp = lastline.split("\"")[1]
            val time = timestamp.split(" ")[1]
            val hours = time.split(":")[0]
            val minutes = time.split(":")[1]
            val timeFinal = hours + ":" + minutes

            val values = lastline.split(":").last().split(",")
            val angle = values[0].trim().substring(1)
            val speed = values[1].trim()
            val pillar = values[2].trim().substring(0, values[2].trim().length - 2)

            if(!isAPillar)
            {
                val output = "  Time: $timeFinal   \n  Angle: $angle \n  Speed: $speed \n  Pillar: $pillar"
                textView.text = output

                val lastEntries = lines.takeLast(5)
                var textEntries: String = ""
                for (line in lastEntries){
                    textEntries = textEntries + "\n" + line
                }
                locationEntries.text = "\n" + textEntries

            }
            else
            {
                textView.text = ""
                locationEntries.text = ""
            }
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        val pillarTextView: TextView = findViewById(R.id.PillarView)
        outState.putString("pillarTextView", pillarTextView.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        /*
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }

         */
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()

    }




}
