package com.chaquo.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.myapplication.databinding.ActivitySensorsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.sql.Timestamp
import kotlin.math.abs
import kotlin.math.pow

class Sensors : AppCompatActivity(), SensorEventListener {

    private val MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 90

    private lateinit var viewBinding: ActivitySensorsBinding

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val linearAccelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var step_count = 0
    private val avg_step_size = 0.76 // meters

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensors)

        viewBinding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        checkPermissions()

        GlobalScope.launch(Dispatchers.IO) {
            step_handler()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
        // You must implement this callback in your code.
    }

    override fun onResume() {
        super.onResume()

        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { pedometer ->
            sensorManager.registerListener(
                this,
                pedometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
//        step_handler()
    }

    override fun onPause() {
        super.onPause()

        // Don't receive any more updates from either sensor.
        sensorManager.unregisterListener(this)
    }

    // Get readings from accelerometer and magnetometer. To simplify calculations,
    // consider storing these readings as unit vectors.
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            System.arraycopy(event.values, 0, linearAccelerometerReading, 0, linearAccelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            step_count = event.values[0].toInt()
            Log.d("STEPS", step_count.toString())
        }
//        step_handler()
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    private fun updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // "orientationAngles" now has up-to-date information.
    }

    private fun getAzimuth(): Int {
        updateOrientationAngles()

        // looking for Azimuth
        var azimuth = (this.orientationAngles[0] * 180 / 3.14).toInt()
        if (azimuth < 0) {
            azimuth += 360
        }

        return azimuth
    }

    private fun randomPillar(pillar: String, chance: Float): List<Any> {
        val random = java.util.Random()
        val randOdd = random.nextFloat()
        if (chance > randOdd) {
            val randomLetter = 'A' + random.nextInt(('P' - 'A') + 1)
            return listOf(true, randomLetter.toString())
        }
        return listOf(false, pillar)
    }

    private suspend fun step_handler() {
        var lastSteps: Int = step_count
        var currentSteps: Int
        var angle: Int = 0
        var distance: Double = 0.0
        var pillar: String = "A"
        val INTERVAL: Long = 120 // seconds
        while (true) {
            var x: Double = 0.0
            var y: Double = 0.0
            val startTime = System.currentTimeMillis()
            for (i in 0 until INTERVAL) {
                delay(1000)
                currentSteps = step_count - lastSteps
                lastSteps = step_count
                distance = currentSteps * avg_step_size
                angle = getAzimuth()
                val coords = get_coord(distance, angle.toDouble())
                x += coords[0]
                y += coords[1]
                val res = randomPillar(pillar, 0.3f)
                if (abs(coords[0]) > 0 && res[0] as Boolean) {
                    pillar = res[1] as String
                    break
                }
            }
            val comp = get_comp(x, y)
            x = 0.0
            y = 0.0
            // velo in m/s
            val timeDiff = (System.currentTimeMillis() - startTime) / 1000
            val data = comp[1].toFloat().toString() + "," + (comp[0] / timeDiff).toFloat() + "," + pillar
            Log.d("movdata", data.toString())
            saveJson(data.toString())
        }
    }

    private fun readJson(fileName: String) {
        val fileName = "${Helper().getLocalUserName(applicationContext)}.json"
        val fileInputStream = openFileInput(fileName)
        val jsonString = fileInputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        Log.d("json read", jsonObject.toString())
        regionHandler(jsonObject)
    }

    private fun saveJson(jsonString: String) {
        val STORAGE_TIME = 3 * 3600 // in seconds: x hours * seconds
        val userNumber: String = Helper().getLocalUserName(applicationContext)
        val fileName = "${userNumber}.json"
        val file = File(filesDir, fileName)
        val jsonObject: JSONObject
        if (file.exists()) {
            val fileInputStream = openFileInput(fileName)
            val jsonFileString = fileInputStream.bufferedReader().use { it.readText() }
            jsonObject = JSONObject(jsonFileString)
            fileInputStream.close()
        }
        else {
            jsonObject = JSONObject()
        }

        Log.d("json file", jsonObject.toString())
        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        jsonObject.put(Timestamp(System.currentTimeMillis()).toString(), jsonString)

        // limit ot 40 items in json object for testing
        while (jsonObject.length() > 40) {
            val firstKey = jsonObject.keys().next()
            jsonObject.remove(firstKey)
        }
        while (jsonObject.length() > 0) {
            val firstKey = jsonObject.keys().next()
            if ((Timestamp(System.currentTimeMillis()).time - Timestamp.valueOf(firstKey).time) / 1000 > STORAGE_TIME)
                jsonObject.remove(firstKey)
            else
                break
        }

        val jsonOutString = jsonObject.toString()
        fileOutputStream.write(jsonOutString.toByteArray())
        fileOutputStream.close()

        runOnUiThread {
            displayJson(jsonOutString)
        }

        updateTimestampFile(userNumber.toInt())
        Log.d("json", file.toString())
        readJson(fileName)
    }

    fun updateTimestampFile(userNumber: Int, currentTimestamp: Timestamp = Timestamp(System.currentTimeMillis())){
        val userNumberIdx = userNumber - 1
        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        var timestampString: String

        if (file.exists()) {
            val rows = file.bufferedReader().readText()
            val csv = rows.split(",").toMutableList()
            Log.d("json", userNumber.toString())
            while (csv.size < userNumber) {
                csv.add(Timestamp(0).toString())
            }
            csv[userNumberIdx] = currentTimestamp.toString()
            timestampString = csv.joinToString(",")
            Log.d("json timestamp", timestampString.toString())
        }
        else {
            timestampString = ""
            for (i in 0 .. userNumberIdx) {
                timestampString += if (i == userNumberIdx) {
                    Timestamp(System.currentTimeMillis()).toString()
                } else {
                    Timestamp(0).toString() + ","
                }
            }
        }

        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        fileOutputStream.write(timestampString.toByteArray())
        fileOutputStream.close()

    }

    private fun displayJson(m: String) {
        val minerDisplay: TextView = findViewById<TextView>(R.id.miner_data)
        minerDisplay.movementMethod = ScrollingMovementMethod()
        minerDisplay.text = "Miner Data:\n$m"
    }

    fun get_coord(magnitude: Double, degrees: Double): List<Double> {
        val angle = Math.toRadians(degrees)
        val x = magnitude * Math.cos(angle)
        val y = magnitude * Math.sin(angle)
        return listOf(x,y)
    }

    fun get_comp(x: Double, y: Double): List<Double> {
        val mag = (x.pow(2) + y.pow(2)).pow(0.5)
        var angle = Math.toDegrees(Math.atan2(y, x))
//        val angle = Math.toDegrees(Math.acos(y / mag))
        if (angle < 0)
            angle += 360

        return listOf(mag, angle)
    }

    private fun ratios(jsonObject: JSONObject, timespan: Int = 10800): Map<String, Float> {
        val pillarCounts = mutableMapOf<String, Int>()
        var lastPiller = "none"
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

    fun regionRatios(pillarRatios: Map<String, Float>): MutableMap<String, Float> {
        val regions = mutableMapOf("ABCD" to 0f, "EFGH" to 0f, "IJKL" to 0f, "MNOP" to 0f)

        for ((key, value) in pillarRatios) {
            for ((rKey, rValue) in regions) {
                if (rKey.contains(key)) {
                    regions[rKey] = regions[rKey]!! + value
                }
            }
        }

        Log.d("regionsratios", regions.toString())
        return regions
    }

    fun regionHandler(jsonObject: JSONObject) {
        val pillar30 = ratios(jsonObject, 30*60)
        val pillar60 = ratios(jsonObject, 60*60)
        val pillar120 = ratios(jsonObject, 120*60)
        val region30 = regionRatios(pillar30)
        val region60 = regionRatios(pillar60)
        val region120 = regionRatios(pillar120)

        runOnUiThread {
            val regions30Display: TextView = findViewById<TextView>(R.id.region_data30)
            regions30Display.text = "Region Data (30 min):\n${String.format("%.2f",region30)}\n"
            val regions60Display: TextView = findViewById<TextView>(R.id.region_data60)
            regions60Display.text = "Region Data (60 min):\n$region60\n"
            val regions120Display: TextView = findViewById<TextView>(R.id.region_data120)
            regions120Display.text = "Region Data (120 min):\n$region120\n"
        }
    }
}
