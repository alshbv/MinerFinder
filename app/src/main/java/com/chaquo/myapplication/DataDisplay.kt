package com.chaquo.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.myapplication.databinding.ActivitySensorsBinding
import org.json.JSONObject
import java.io.File
import java.sql.Timestamp

class DataDisplay : AppCompatActivity() {
    private lateinit var viewBinding: ActivitySensorsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensors)

        viewBinding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        val userNumber = Helper().getLocalUserName(applicationContext).toInt()

        val handlerThread = HandlerThread("MyHandlerThread")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        val runnable = object : Runnable {
            override fun run() {
                // your code here
                readJson("$userNumber.json")

                handler.postDelayed(this, 10 * 1000) // 10 seconds
            }
        }
        handler.post(runnable)
    }

    private fun readJson(fileName: String) {
        val fileName = "${Helper().getLocalUserName(applicationContext)}.json"
        if (!File("$filesDir/$fileName").exists()) {
            Log.d("filetree", "file does not exitt")

            return
        }
        val fileInputStream = openFileInput(fileName)
        val jsonString = fileInputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        Log.d("json read", jsonObject.toString())

        regionHandler(jsonObject)
        runOnUiThread {
            displayJson(jsonString)
        }

    }

    private fun displayJson(m: String) {
        val minerDisplay: TextView = findViewById<TextView>(R.id.miner_data)
        minerDisplay.movementMethod = ScrollingMovementMethod()
        minerDisplay.text = "Miner Data:\n$m"
    }

    private fun ratios(jsonObject: JSONObject, timespan: Int = 10800): Map<String, Float> {
        val pillarCounts = mutableMapOf<String, Int>()
        var isFirst = true
        var lastPiller = "none"
        var timeDiff = 0
        for (key in jsonObject.keys()) {
            val values = jsonObject.get(key).toString().split(",")
            val pillar = values[2]
            val time = Timestamp.valueOf(key)

            // check if were within timespan
            if ((System.currentTimeMillis() - time.time) / 1000 > timespan) {
                continue
            }

            // only add if changing pillars
            if (lastPiller == pillar) {
                continue
            }
            lastPiller = pillar

            if (pillarCounts.containsKey(pillar)) {
                pillarCounts[pillar] = pillarCounts[pillar]!! + 1
            } else {
                pillarCounts[pillar] = 1
            }
        }

        Log.d("ratios", pillarCounts.toString())

        val total = pillarCounts.values.sum()
        val letterRatios = pillarCounts.mapValues { (_, count) -> count.toFloat() / total }
        Log.d("ratios", letterRatios.toString())

        return letterRatios
    }

    fun regionRatios(pillarRatios: Map<String, Float>): String {
        val regions = mutableMapOf("ABCD" to 0f, "EFGH" to 0f, "IJKL" to 0f, "MNOP" to 0f)

        for ((key, value) in pillarRatios) {
            for ((rKey, rValue) in regions) {
                if (rKey.contains(key)) {
                    regions[rKey] = regions[rKey]!! + value
                }
            }
        }

        var out = ""
        for ((key, value) in regions) {
            out += key.toString() + "=" + String.format("%.2f",value)
        }

        Log.d("regionsratios", regions.toString())
        return out
    }

    fun regionHandler(jsonObject: JSONObject) {
        val pillar30 = ratios(jsonObject, 30*60)
        val pillar60 = ratios(jsonObject, 60*60)
        val pillar90 = ratios(jsonObject, 90*60)
        val pillar120 = ratios(jsonObject, 120*60)
        val region30 = regionRatios(pillar30)
        val region60 = regionRatios(pillar60)
        val region90 = regionRatios(pillar90)
        val region120 = regionRatios(pillar120)

        runOnUiThread {
            val regions30Display: TextView = findViewById<TextView>(R.id.region_data30)
            regions30Display.text = "Region Data (30, 60, 90, 120 min):\n$region30\n$region60\n$region90\n$region120\n"
            val regions60Display: TextView = findViewById<TextView>(R.id.region_data60)
            regions60Display.text = "Region Data (60, 120 min):\n$region60\n$region120\n"
            val regions120Display: TextView = findViewById<TextView>(R.id.region_data120)
            regions120Display.text = "Region Data (120 min):\n$region120\n"
        }
    }
}