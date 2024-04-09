package com.chaquo.myapplication

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import com.chaquo.myapplication.databinding.ActivitySensorsBinding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.sql.Timestamp
import kotlin.math.pow


class MyPayloadCallback(private val context: Context) : PayloadCallback() {
    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        // Handle the received payload data
        val receivedData = String(payload.asBytes()!!)
        Log.d("RECIEVEJSON", "$receivedData")
        saveReceivedJson(receivedData)
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        // Handle the transfer update, e.g., progress or completion
    }

    private fun saveReceivedJson(jsonString: String) {
        // Specify the subfolder name where you want to save the received JSON data
        val subfolderName = "MinerData"
        val subfolderName2 = "PillarData"
        val subfolder = File(context.filesDir, subfolderName)
        val subfolder2 = File(context.filesDir, subfolderName2)
        // Create the subfolder if it doesn't exist
        if (!subfolder.exists()) {
            subfolder.mkdirs()
        }
        if (!subfolder2.exists()) {
            subfolder2.mkdirs()
        }

        // Create a unique file name for each JSON payload (you can use a timestamp, UUID, etc.)
        val fileName = "received_payload_${System.currentTimeMillis()}.json"
        val file = File(subfolder, fileName)
        val file2 = File(subfolder2, fileName)

        // Write the JSON data to the file
        file.writeText(jsonString)
        file2.writeText(jsonString)
    }
}

class StepCounter : Service(){

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

    private lateinit var localUserName: String

    private var currentJsonData: String = ""

    // for the connection
    val allPillarList: List<Char> = ('A'..'Z').toList()
    val pillarCurrentlyReceived = mutableListOf<String>()
    val minersCurrentlyFound = mutableListOf<String>()
    private val links = mutableListOf<List<String>>()
    var advertisingID: String = ""
    private val SERVICE_ID = "MinerFinder_Pillar"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this
    private var isAdvertising=false
    private var isDiscovering=false

    // Binder lets us call public fns we define
    // in other activites.
    inner class MyBinder : Binder() {
        fun getService(): StepCounter = this@StepCounter
    }

    private val binder = MyBinder()

    fun initiateAutomaticConnection() {
        if (!isAdvertising) {
             startAdvertising()
        }
        if (!isDiscovering) {
             startDiscovery()
        }

    }
    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.TOP , 0, 0)
        toast.show()
    }
    private val payloadCallback: PayloadCallback = MyPayloadCallback(this)

    private val mStepCounterListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // Do something here if sensor accuracy changes.
            // You must implement this callback in your code.
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
                //Log.d("STEPS", step_count.toString())
            }
//        step_handler()
            updateOrientationAngles()
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        GlobalScope.launch(Dispatchers.IO) {
            step_handler()
        }

        Log.d("SERVICE", "here")

        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                mStepCounterListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                mStepCounterListener,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { accelerometer ->
            sensorManager.registerListener(
                mStepCounterListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also { pedometer ->
            sensorManager.registerListener(
                mStepCounterListener,
                pedometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
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


    // just for testing
    companion object {
        const val ACTION_SET_PILLAR = "com.example.stepcounter.SET_PILLAR"
        const val EXTRA_PILLAR_VALUE = "pillar_value"
    }

    var testPillar = "A"

    private fun getPillar():String
    {
        return testPillar
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (it.action == ACTION_SET_PILLAR) {
                    val newValue = it.getStringExtra(EXTRA_PILLAR_VALUE)
                    newValue?.let { value ->
                        testPillar = value
                        // Do any additional processing with the updated testPillar variable here
                    }
                }
            }
        }
    }

    // remove the code up above.
    // once we don't need to manually set locations.

    internal fun startAdvertising() {
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        isAdvertising=true
        val endpointName = advertisingID
        Nearby.getConnectionsClient(context)
            .startAdvertising(
                endpointName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                Log.d("Pillar_Advertising:", endpointName)
                isAdvertising=true
                // this should also update the activity I call it from with runOnUI.
                // I am not sure what's a good way to do this atm
            }
            .addOnFailureListener { e: Exception? ->
                Log.d("Advertising as Pillar:", "Failed")
            }
    }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {

            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                Log.d("CONINFO", connectionInfo.toString())
                Log.d("CONINFO", endpointId.toString())
                Log.d("CONINFO", context.toString())

                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {

                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        Log.d("CONINFO", "Connected")
                        saveJson2(currentJsonData, endpointId)
                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        Log.d("CONINFO", "Rejected")

                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        Log.d("CONINFO", "Error")

                    }
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                Log.d("DISCONNECTED", "start disconnecting")

            }
        }


    internal fun startDiscovery() {
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                //this.isDiscovering = true
                isDiscovering=true
                Log.d("Pillar_ID", "Started Discovering Pillars")
            }
            .addOnFailureListener { e: java.lang.Exception? -> }
    }

    fun stopAdvertising() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        advertisingID = ""
        isAdvertising=false
    }

    fun stopDiscovery() {
        Nearby.getConnectionsClient(context).stopDiscovery()
        //this.isDiscovering = false
        isDiscovering=false
    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                val discoveredEndpointName = info.endpointName
                val discoveredEndpointID = endpointId
                // PhotoConnection2.links.add(listOf(discoveredEndpointID, discoveredEndpointName.dropLast(1)))
                showToast("${discoveredEndpointName.dropLast(1)}.csv received")
                Log.d("Connection-Pillar", "Pillar $discoveredEndpointName found")

                // add the pillar to the map.
                if (discoveredEndpointName.length == 2 && discoveredEndpointName[1] == 'P' && discoveredEndpointName[0] in allPillarList) {
                    Log.d("Pillar:added", "$discoveredEndpointName")
                    links.add(listOf(discoveredEndpointID.toString(), discoveredEndpointName.dropLast(1)))
                    pillarCurrentlyReceived.add(discoveredEndpointName.dropLast(1))
                    Log.d("Pillar:total", pillarCurrentlyReceived.toString())
                }
                if (discoveredEndpointName.length == 2 && discoveredEndpointName.lastOrNull() == 'M') {
                    links.add(listOf(discoveredEndpointID.toString(), discoveredEndpointName.dropLast(1)))
                    minersCurrentlyFound.add(discoveredEndpointName.dropLast(1))
                    Log.d("Miner:added", "$discoveredEndpointName")
                    initiateConnection(endpointId)
                }

            }

            override fun onEndpointLost(endpointId: String) {
                // Update the list of pillar
                val pillarLost = links.find { it[0] == endpointId }
                var pillarName = pillarLost?.get(1).toString()

                Log.d("Pillarname/MinerName!", pillarName)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    links.removeIf { it[0] == endpointId }
                    pillarCurrentlyReceived.removeIf {it == pillarName}
                    minersCurrentlyFound.removeIf {it == pillarName}
                    Log.d("Tried to Remove", "$pillarName")
                }
            }
        }

    // needs to be public so we can call with our Service
    fun setPillar(pillarName: String)
    {
        if (pillarName.length == 1 && pillarName[0] in allPillarList) {
            stopAdvertising()
            Log.d("Pillar_Connection", "tried to start advertising as $pillarName")
            advertisingID = pillarName + "P"
            startAdvertising()
        }
    }

    fun setMiner()
    {
        stopAdvertising()
        //advertisingID = Helper().getLocalUserName(context) + "M"
        advertisingID = localUserName + "M"
        startAdvertising()
    }

    fun getPillarNew(): String
    {
        if(pillarCurrentlyReceived.isNotEmpty())
        {
            //Log.d("GETPillarNew", pillarCurrentlyReceived[0])
            return pillarCurrentlyReceived[0]
        }
        else
        {
            return ""
        }
    }

    fun getMinerList(): MutableList<String> {
        Log.d("MIner_List", minersCurrentlyFound.toString())
        return minersCurrentlyFound
    }


    override fun onCreate() {
        super.onCreate()
        // Register the BroadcastReceiver
        val filter = IntentFilter(ACTION_SET_PILLAR)
        registerReceiver(receiver, filter)
        startDiscovery()
        advertisingID = Helper().getLocalUserName(context) + "M"
        startAdvertising()
        localUserName = Helper().getLocalUserName(context)
        Log.d("StepCOunter Oncreate!", "onCreate called $localUserName")

        // testing to detect leaks
        /*
        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )
         */

    }


    private suspend fun step_handler() {
        var lastSteps: Int = step_count
        var currentSteps: Int
        var angle: Int = 0
        var distance: Double = 0.0
        var pillar: String = "A"
        // as 5 seconds for testing
        val INTERVAL: Long = 5 // seconds
        while (true) {
            var x: Double = 0.0
            var y: Double = 0.0
            val startTime = System.currentTimeMillis()
            for (i in 0 until INTERVAL) {
                delay(1000)
                //Log.d("STEPCOUNT", step_count.toString() )
                currentSteps = step_count - lastSteps
                //Log.d("STEPCOUNT-CURRENT", currentSteps.toString() )
                lastSteps = step_count
                distance = currentSteps * avg_step_size
                angle = getAzimuth()
                val currentCoords = get_coord(distance, angle.toDouble()) // Use getAzimuth() for angle
                x += currentCoords[0]
                y += currentCoords[1]
                //Log.d("X: ", x.toString())
                //Log.d("Y: ", y.toString())

                /*
                val res = randomPillar(pillar, 0.3f)
                if (abs(coords[0]) > 0 && res[0] as Boolean) {
                    pillar = res[1] as String
                    break
                }

                 */
                val res = randomPillar(pillar, 0.3f)
                var pillar2 = getPillarNew()
                if (pillar2 != pillar && (pillar2.isNotEmpty())) {
                    Log.d("BREAK", "from $pillar to $pillar2")
                    pillar = pillar2
                    break
                }

            }
            val comp = get_comp(x, y)
            // velo in m/s
            val timeDiff = (System.currentTimeMillis() - startTime) / 1000
//            val data = MovementData(comp[1].toFloat(), (comp[0] / timeDiff).toFloat(), pillar)
            val data = comp[1].toFloat().toString() + "," + (comp[0] / timeDiff).toFloat() + "," + pillar
            //Log.d("movdata", data.toString())
            saveJson(data.toString())
        }
    }

    private fun readJson(fileName: String) {
        //val fileName = "${Helper().getLocalUserName(applicationContext)}.json"
        val fileName = "${localUserName}.json"
        val fileInputStream = openFileInput(fileName)
        val jsonString = fileInputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        //Log.d("json read", jsonObject.toString())
    }

    private fun saveJson2(jsonString: String, endpointId: String) {
        // Send the JSON data as a payload
        val payload = Payload.fromBytes(jsonString.toByteArray())
        Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
    }
    private fun saveJson(jsonString: String) {
        val STORAGE_TIME = 3 * 3600 // in seconds: x hours * seconds
        val userNumber: String = Helper().getLocalUserNum(applicationContext)
      //val userNumber: String = localUserName
        val fileName = "${localUserName}.json"
        val file = File(filesDir, fileName)
        var jsonObject: JSONObject
        if (file.exists()) {
            try {
                val fileInputStream = openFileInput(fileName)
                val jsonFileString = fileInputStream.bufferedReader().use { it.readText() }
                Log.d("JSON output", jsonFileString)
                currentJsonData = jsonFileString
               // Log.d("JSON output to send", jsonFileString)


                // Check if the JSON string is not empty
                if (jsonFileString.isNotEmpty()) {
                    jsonObject = JSONObject(jsonFileString)
                } else {
                    jsonObject = JSONObject()
                }

                fileInputStream.close()
            } catch (e: Exception) {
                // Handle exception
                e.printStackTrace()
                jsonObject = JSONObject()
            }

        }
        else {
            file.createNewFile()
            jsonObject = JSONObject()
        }

        //Log.d("json file", jsonObject.toString())
        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        jsonObject.put(Timestamp(System.currentTimeMillis()).toString(), jsonString)

        // limit ot 80 items in json object for testing

        while (jsonObject.length() > 80) {
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

        val userNumberInt = userNumber.toIntOrNull()
        if (userNumberInt != null) {
            updateTimestampFile(userNumberInt)
        } else {

            Log.e("SaveJson", "Failed to convert userNumber to an integer: $userNumber")
        }

        //Log.d("json", file.toString())
        readJson(fileName)
    }

    fun updateTimestampFile(userNumber: Int, currentTimestamp: Timestamp = Timestamp(System.currentTimeMillis())){
        val userNumberIdx = userNumber - 1
        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        var timestampString: String

        if (file.exists()) {
            //val rows = file.bufferedReader().readText()
            //Log.d("Update_Timestamp", "At buffered reader, check")
            val rows = file.bufferedReader().use {
                it.readText()
            }
            val csv = rows.split(",").toMutableList()
            //Log.d("json", userNumber.toString())
            while (csv.size < userNumber) {
                csv.add(Timestamp(0).toString())
            }
            csv[userNumberIdx] = currentTimestamp.toString()
            timestampString = csv.joinToString(",")
            //Log.d("json timestamp", timestampString.toString())
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

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private fun initiateConnection(endpointId: String) {
        // Handle the logic to initiate the connection with the endpoint
        Nearby.getConnectionsClient(context)
            .requestConnection(localUserName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                // Connection request was successful
                Log.d("InitiateConnection", "Connection request successful")
            }
            .addOnFailureListener { e ->
                // Connection request failed
                Log.e("InitiateConnection", "Connection request failed", e)
            }
    }

}