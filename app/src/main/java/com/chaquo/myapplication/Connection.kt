package com.chaquo.myapplication

//import com.google.android.gms.common.util.IOUtils.copyStream

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SimpleArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.myapplication.Connection.SerializationHelper.serialize
import com.chaquo.myapplication.databinding.ActivityConnectionBinding
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.*
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.io.File
import java.util.regex.Pattern
import com.chaquo.myapplication.db.AppDatabase
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.text.SimpleDateFormat
import java.util.Locale


// RENAME TO CONNECTION IF USING AGAIN
class Connection : AppCompatActivity(){
    private val TAG = "Connection"
    private val SERVICE_ID = "Data_Transfer"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    private var isAdvertising: Boolean = false
    private var isDiscovering: Boolean = false
    private var eid : String = ""

    var userNumber: String = "x"

    private lateinit var viewBinding: ActivityConnectionBinding

    private val found_eid = mutableListOf<String>()
    private val global : Global? = null

    val links = mutableListOf<List<String>>() // endpointid, usernumber
    val lost = mutableListOf<String>()
    val offline = mutableListOf<String>()


    // send photo vars
    private val READ_REQUEST_CODE = 42
    private val ENDPOINT_ID_EXTRA = "com.foo.myapp.EndpointId"

    companion object {
        private const val LOCATION_PERMISSION_CODE = 100
    }

    // for binding the service
    private lateinit var stepCounter_Pillar: StepCounter
    var isBound = false
    private var currentID: String = ""
    private var isPillar = false

    // binds the service
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StepCounter.MyBinder
            stepCounter_Pillar = binder.getService()
            isBound = true

            // after binding, look for
            if (stepCounter_Pillar.advertisingID.isNotEmpty()) {
                Log.d("SAVEDSTATE", "HERE")
                currentID = stepCounter_Pillar.advertisingID.dropLast(1)
                isPillar = (stepCounter_Pillar.advertisingID.last() == 'P')
            }

            // finally, in the onCreate, suspend the service's connection
            stepCounter_Pillar.stopDiscovery()
            stepCounter_Pillar.stopAdvertising()

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Handle disconnection
            isBound = false
        }
    }

    override fun onBackPressed() {
        Log.d("ONCREATE-DESTROY", "photo onback called")

        while(links.isNotEmpty())
        {
            disconnectEndpoint(links[0][0])
        }
        modeOff()

        if (isPillar)
        {
            stepCounter_Pillar.setPillar(currentID)
        }
        else
        {
            stepCounter_Pillar.setMiner()
        }
        super.onBackPressed()
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        Log.d("ONCREATE-DESTROY", "Oncreate called")
        val global = this
        userNumber = Helper().getLocalUserName(applicationContext)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE)

        // bind to the service
        // for now, not calling any specific methods
        val intent = Intent(this, StepCounter::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)


        viewBinding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.offButton.setOnClickListener {
            /*
            if(links.isNotEmpty()) {
                for (i in links.indices) {
                    Log.d("Bjorn", "$i run of links")
                    disconnectEndpoint(links[i][0])
                }
            }
            else
            {
                Log.d("connection","Links is empty")
            }

             */

            while(links.isNotEmpty())
            {
                disconnectEndpoint(links[0][0])
            }
            modeOff()
        }

        viewBinding.bothButton.setOnClickListener {
            startAdvertising(false)
            startDiscovery(false)

            val connectionMode: TextView = findViewById<TextView>(R.id.connection_mode)
            connectionMode.text = "Connection Mode: ON"

            Log.d("Connection", isAdvertising.and(isDiscovering).toString())

            val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
            connectionReport.text = "Searching"
            //modeDisplay()
            Log.d("initial", "modedisplay")
        }

        viewBinding.sendPhotoButton.setOnClickListener {

            /*
            if (global.found_eid.isNotEmpty()) {
                val firstEid = global.found_eid[0]
                // sendPhoto
                Log.d("haseid", firstEid)
                sendPhoto(firstEid)
            }
            Log.d("haseid", "no :(")
                         */
            deleteJsonFiles()
            deleteCSVFiles()
            //sendPhotos2()
        }

        viewBinding.updateDataButton.setOnClickListener{
            updateFiles()
        }
    }

      ////////////////
     // SEND PHOTO //
    ////////////////
    private fun sendPhoto(endpointId: String) {
        showImageChooser(endpointId)
    }


    private fun showImageChooser(endpointId: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        intent.putExtra(ENDPOINT_ID_EXTRA, endpointId)
        startActivityForResult(intent, READ_REQUEST_CODE)
        //Log.d(TAG, "end img")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK && resultData != null) {
//            val endpointId = resultData.getStringExtra(ENDPOINT_ID_EXTRA)
            val endpointId = this.eid
            //Log.d("EID", endpointId.toString())

            // The URI of the file selected by the user.
            val uri = resultData.data
            val filePayload: Payload
            filePayload = try {
                // Open the ParcelFileDescriptor for this URI with read access.
                val pfd = contentResolver.openFileDescriptor(uri!!, "r")
                Payload.fromFile(pfd!!)
            } catch (e: FileNotFoundException) {
                Log.e("MyApp", "File not found", e)
                return
            }

            // Construct a simple message mapping the ID of the file payload to the desired filename.
            val filenameMessage = filePayload.id.toString() + ":" + uri.lastPathSegment + "2"

            //Log.d("FILENAME", filenameMessage)

            // Send the filename message as a bytes payload.
            val filenameBytesPayload = Payload.fromBytes(serialize(filenameMessage))
                //Payload.fromBytes(filenameMessage.toByteArray(StandardCharsets.UTF_8))
            Nearby.getConnectionsClient(context).sendPayload(endpointId!!, filenameBytesPayload)

            // Finally, send the file payload.
            if(endpointId != null) {
                //Log.d(TAG, "in result")

                Nearby.getConnectionsClient(context).sendPayload(endpointId, filePayload).addOnSuccessListener {
                    //Log.d(TAG, "successful send?")
                }
            }
        }
    }

      //////end///////
     // SEND PHOTO //
    ////////////////


    // For testing a constant connection
    private suspend fun constantSend(endpointId: String) {
        var flag = true
        while(flag){
            val timestamp = Timestamp(System.currentTimeMillis())
            val bytesPayload = Payload.fromBytes(serialize(timestamp))
            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
                .addOnSuccessListener { unused: Void? -> }
                .addOnFailureListener { e: java.lang.Exception? ->
                    flag = false
                }
            delay(1000)
        }
    }

    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this,permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
        else {
            //Log.d(TAG, "Permissions not denied")
        }
    }

    private fun modeDisplay() {
        var mode: String = "OFF"
        if (isAdvertising && isDiscovering) {
            mode = "ON"
            Log.d("final", "modeDisplay")
        }
        else if (isAdvertising) {
            //mode = "ADVERTISING"
        }
        else if (isDiscovering) {
            //mode = "DISCOVERING"
        }
        val connectionMode: TextView = findViewById<TextView>(R.id.connection_mode)
        connectionMode.text = "Connection Mode: $mode"

    }

    private fun errorDisplay(e: String) {
//        val errorLog: TextView = findViewById<TextView>(R.id.error_log)
//        Log.d("errorlog", e)
//        errorLog.text = "Error Log: $e"
    }

    private fun connectionDisplay(m: String) {
        val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
        connectionReport.text = "Connection Status: $m"
    }

    private fun messageDisplay(m: String) {
        val dataDisplay: TextView = findViewById<TextView>(R.id.data_received)
        dataDisplay.text = "Received: $m"
    }

    private fun linksDisplay() {
        runOnUiThread {
            val linksDisplay: TextView = findViewById<TextView>(R.id.links)
            val linksNumbers = links.map { it[1] }
            //linksDisplay.text = "Links/lost: $linksNumbers / $lost"
            Log.d("Connection Links", links.toString())
            linksDisplay.text = "Connected to: $linksNumbers"
        }
    }

    private fun offlineDisplay() {
        runOnUiThread {
            val offlineDisplay: TextView = findViewById<TextView>(R.id.offline)
            //offlineDisplay.text = "Universal offline: $offline"
        }
    }

    private fun startAdvertising(singleMode: Boolean = true) {
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        /*
        if(isDiscovering && singleMode)
            stopDiscovery()
         */

        Nearby.getConnectionsClient(context)
            .startAdvertising(
                userNumber, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                isAdvertising = true
                //modeDisplay()
            }
            .addOnFailureListener { e: Exception? ->
                errorDisplay("Advertising Failed: " + e.toString())
            }
    }

    private fun startDiscovery(singleMode: Boolean = true) {
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        /*
        if(isAdvertising && singleMode)
            stopAdvertising()
         */
        //Log.d("FUNCTION", "sd")


        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                isDiscovering = true
                //modeDisplay()
            }
            .addOnFailureListener { e: java.lang.Exception? ->
                errorDisplay("Discovery Failed: " + e.toString())
            }
    }

    private fun modeOff() {
        if(isAdvertising)
            stopAdvertising()
        if (isDiscovering)
            stopDiscovery()
        modeDisplay()
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        isAdvertising = false
        //modeDisplay()
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(context).stopDiscovery()
        isDiscovering = false
        //modeDisplay()
    }

    private fun disconnectEndpoint(endpointId: String = eid) {
        Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId)
        val lostNumber = links.find { it[0] == endpointId }

        //connectionDisplay("Disconnected from $lostNumber[0]")

        if (lostNumber != null) {
            val alreadyExists = lost.contains(lostNumber[0])
            if(!alreadyExists) {
                lost.add(lostNumber[1])
//                offline.add(lostNumber[1])
                links.remove(lostNumber)
            }
            sendLostMessage(lostNumber[1].toString())
        }
        linksDisplay()
//        offlineDisplay()

    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // An endpoint was found. We request a connection to it.
//                stopDiscovery()
                //Log.d("ENDPOINT FOUND", info.endpointName)
                val endpointName = info.endpointName
                Nearby.getConnectionsClient(context)
                    .requestConnection(userNumber, endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener { unused: Void? ->
                        //connectionDisplay("Connected to $endpointName.")
                        global?.found_eid?.add(endpointId)
                        //Log.d("eidlist", global?.found_eid.toString())
                        //links.add(listOf(endpointId, endpointName))
                        //linksDisplay()
                    }
                    .addOnFailureListener { e: java.lang.Exception? ->
//                        connectionDisplay("Found endpoint. Failed to request connection.") // rm for display
                        errorDisplay(e.toString())
                    }
//                startDiscovery(false)
            }

            override fun onEndpointLost(endpointId: String) {
                // A previously discovered endpoint has gone away.
                //Log.d("status", "lost")
                //val lostEndpoint = links.find { it[0] == endpointId }
                //connectionDisplay("Lost endpoint: $lostEndpoint")
            }
        }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Automatically accept the connection on both sides.
                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        //connectionDisplay("Made a connection")
                        sendTimestamps(endpointId)

                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        connectionDisplay("Connection Rejected")
                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        errorDisplay("Failed to connect. Status Error.")
                    }
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received.
                //Log.d("status", "disconnected")
                connectionDisplay("Disconnected from endpoint.")
                val lostNumber = links.find { it[0] == endpointId }
                if (lostNumber != null) {
                    val alreadyExists = lost.contains(lostNumber[0])
                    if(!alreadyExists) {
                        lost.add(lostNumber[1])
                        offline.add(lostNumber[1])
                        links.remove(lostNumber)
                    }
                    sendLostMessage(lostNumber[1].toString())
                }
                linksDisplay()
                //offlineDisplay()
            }
        }

    private var incomingFilePayloads = SimpleArrayMap<Long, Payload>()
    private var completedFilePayloads = SimpleArrayMap<Long, Payload>()
    private var filePayloadFilenames = SimpleArrayMap<Long, String>()

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // This always gets the full data of the payload. Is null if it's not a BYTES payload.
            if (payload.type == Payload.Type.BYTES) {
                val receivedBytes = SerializationHelper.deserialize(payload.asBytes())
                //Log.d("MESSAGE", receivedBytes.toString())

//                val dataDisplay: TextView = findViewById<TextView>(R.id.data_received)
//                dataDisplay.text = "Message: $receivedBytes"
                //connectionDisplay("Message received.")

                evalMessage(receivedBytes.toString(), endpointId)

                // send a message back for TESTING
//                if(isDiscovering) {
//                    val bytesPayload = Payload.fromBytes(serialize("RECEIPT: $receivedBytes"))
//                    Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
//                }

                eid = endpointId
//                Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId)
            }
            else if (payload.type == Payload.Type.FILE)
            {
                //Log.d(TAG, "receiving file?")
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingFilePayloads.put(payload.id, payload);

                val fileUri = payload.asFile()!!.asUri()
                //Log.d("saveimage", fileUri.toString())
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().

            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payloadId = update.payloadId
                val payload = incomingFilePayloads.remove(payloadId)
                if (payload != null && payload!!.type == Payload.Type.FILE) {
                    completedFilePayloads.put(payloadId, payload)
                    processFilePayload(payloadId)
                }
            }
        }
    }

    /** Helper class to serialize and deserialize an Object to byte[] and vice-versa  */
    object SerializationHelper {
        @Throws(IOException::class)
        fun serialize(`object`: Any?): ByteArray {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            // transform object to stream and then to a byte array
            objectOutputStream.writeObject(`object`)
            objectOutputStream.flush()
            objectOutputStream.close()
            return byteArrayOutputStream.toByteArray()
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        fun deserialize(bytes: ByteArray?): Any {
            val byteArrayInputStream = ByteArrayInputStream(bytes)
            val objectInputStream = ObjectInputStream(byteArrayInputStream)
            return objectInputStream.readObject()
        }


    }


    // HANDLE FILE TRANSFERS

    // add 0 to end of file if its timestamps; 1 if its miner data ; 2 for a file name

    fun evalMessage(message: String, endpointId: String) {
        //Log.d("evalmes", message)

        if (message.contains("lost connection to")) {
            messageDisplay(message)
                    offline.add(message.last().toString())
            offlineDisplay()
        }
        else if (message.contains("regained connection to")) {
            messageDisplay(message)
            offline.remove(message.last().toString())
            offlineDisplay()
        }
        else if (message.last() == '0') {
            GlobalScope.launch(Dispatchers.IO) {
                val newMessage = message.dropLast(1).split(",")
                val otherUser = newMessage.last()

                if (lost.contains(otherUser)) {
                    lost.remove(otherUser)
                }

                val userNumber = links.find { it[1] == otherUser }
                if (userNumber == null) {
                    links.add(listOf(endpointId, otherUser))
                    linksDisplay()
                }
                if(offline.contains(otherUser)) {
                    offline.remove(otherUser)
                    sendOnline(otherUser)
                }


                //Log.d("stampbug", newMessage.dropLast(1).toString())
                evalTimestamps(newMessage.dropLast(1).joinToString(), endpointId)
                runOnUiThread {
                    connectionDisplay("Received timestamp.csv from User #$otherUser")
                }
            }
        }
        else if (message.last() == '1') {
            readMiner(message.dropLast(1))
        }
        else if (message.last() == '2')
        {
            //val payloadId: Long = addPayloadFilename(message.dropLast(1))
            //processFilePayload(payloadId)
            //Log.d(TAG, message)
        }
    }

    // remove later if not needed
    private fun addPayloadFilename(payloadFilenameMessage: String): Long {
        val parts = payloadFilenameMessage.split(":").toTypedArray()
        val payloadId = parts[0].toLong()
        val filename = parts[1]
        //Log.d("NAME", filename)

        filePayloadFilenames.put(payloadId, filename)
        return payloadId
    }

    private fun processFilePayload(payloadId: Long) {
        //Log.d("PATH", "IN PROCFILE")
        // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
        // payload is completely received. The file payload is considered complete only when both have
        // been received.
        val filePayload = completedFilePayloads[payloadId]
        val filename: String? = filePayloadFilenames.get(payloadId)
        if(filename != null)
            //Log.d("PFP", filename)
        if (filePayload != null && filename != null) {
            completedFilePayloads.remove(payloadId)
            filePayloadFilenames.remove(payloadId)

            //Log.d("DOWN", "ABOVE REMOVE DOWN")

            // Get the received file (which will be in the Downloads folder)
            // Because of https://developer.android.com/preview/privacy/scoped-storage, we are not
            // allowed to access filepaths from another process directly. Instead, we must open the
            // uri using our ContentResolver.
            val uri: Uri? = filePayload.asFile()!!.asUri()

            //lateinit var imageView: ImageView
            //imageView = findViewById(R.id.imageView)
            //imageView.setImageURI(uri)

            saveToPhotos(uri)
        }
    }

    private fun saveToPhotos(uri: Uri?) {
        val imageTitle = "My Image Title"
        val imageDescription = "My Image Description"

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())


        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.TITLE, imageTitle)
            put(MediaStore.Images.Media.DESCRIPTION, imageDescription)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                //Log.d("image1", MediaStore.Images.Media.RELATIVE_PATH)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Received-Images-MinerFinder")
            }
        }

        //Log.d("image2", MediaStore.Images.Media.RELATIVE_PATH)


        val contentResolver = context.contentResolver
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (imageUri != null) {
            val outputStream = contentResolver.openOutputStream(imageUri)
            val inputStream = uri?.let { context.contentResolver.openInputStream(it) }
            if (inputStream != null && outputStream != null) {
                try {
                    inputStream.copyTo(outputStream)
                } catch (e: Exception) {
                    Log.e("SaveToPhotos", "Error copying image: ${e.message}")
                } finally {
                    inputStream.close()
                    outputStream.close()
                }
            }

        } else {
            Log.e("SaveToPhotos", "Failed to save image to MediaStore.")


        }

    }


    //




    fun sendTimestamps(endpointId: String) {
        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        var contents = ""
        if (file.exists()) {
            contents = file.bufferedReader().readText() + ",$userNumber" + "0"
        }
        else {
            contents = "$userNumber" + "0"
        }
        val bytesPayload = Payload.fromBytes(serialize(contents))
        Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)

    }

    suspend fun evalTimestamps(partnerStamps: String, endpointId: String) {
        val partnerCSV = partnerStamps.split(",").toMutableList()

        val fileName = "timestamp.csv"
        val file = File(filesDir, fileName)
        var timestampString: String

        if (file.exists()) {
            val rows = file.bufferedReader().readText()
            val myCSV = rows.split(",").toMutableList()

            for (i in 1 until myCSV.size) {
                // if partner doesn't have that file or mine is newer send it to them
                if (i > partnerCSV.size-1 || Timestamp.valueOf(myCSV[i]) > Timestamp.valueOf(partnerCSV[i])) {
                    sendMiner(endpointId, i+1, Timestamp.valueOf(myCSV[i]))
                    delay(1000)
                }
            }
        }
    }

    fun sendMiner(endpointId: String, minerNumber: Int, timestamp: Timestamp) {
        val fileName = "$minerNumber.json"
        val file = File(filesDir, fileName)
        if (!file.exists()) {
            if (minerNumber.toString() == userNumber) {
                updateTimestampFile(minerNumber)
                return // maybe should create file
            }
            else {
                return
            }
        }
        //Log.d("csv%", minerNumber.toString())
        val contents = file.readText() + ",$minerNumber,$timestamp" + "1"
        val bytesPayload = Payload.fromBytes(serialize(contents))
        Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)

    }

    fun readMiner(message: String) {
        val csv = message.split(",").toMutableList()
        //Log.d("csvbug", csv.toString())
        val minerNumber: Int = csv[csv.size.toInt()-2].toInt()
        val timestamp: Timestamp = Timestamp.valueOf(csv[csv.size.toInt()-1])
        csv.removeAt(csv.size.toInt()-1)
        csv.removeAt(csv.size.toInt()-1)
        messageDisplay("Received $minerNumber.json")
        //Log.d("csv", message)
        //Log.d("csv#", minerNumber.toString())
        ConnectionCheck.myList.remove(minerNumber.toString())


        // update miner data file
        val fileName = "$minerNumber.json"
        val fileOutputStream = openFileOutput(fileName, Context.MODE_PRIVATE)
        fileOutputStream.write(csv.joinToString().toByteArray())
        fileOutputStream.close()

        // update timestamp file
        updateTimestampFile(minerNumber, timestamp)
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

    fun sendLostMessage(number: String) {
        val linksNumbers = links.map { it[0] }
        for (endpointId in linksNumbers) {
            val contents = "lost connection to $number"
            val bytesPayload = Payload.fromBytes(serialize(contents))
            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
        }
    }

    fun sendOnline(number: String) {
        val linksNumbers = links.map { it[0] }
        for (endpointId in linksNumbers) {
            val contents = "regained connection to $number"
            val bytesPayload = Payload.fromBytes(serialize(contents))
            Nearby.getConnectionsClient(context).sendPayload(endpointId, bytesPayload)
        }

    }


    // this is only being used for testing
    fun deleteJsonFiles() {
        val directory = context.getFilesDir()
        val excludeFile = "${Helper().getLocalUserName(applicationContext)}.json"

        // Check if the directory exists
        if (directory.exists() && directory.isDirectory) {
            val jsonFiles = directory.listFiles { _, fileName ->
                // Define a regular expression pattern to match single-digit number JSON files
                val pattern = Pattern.compile("^[0-9]\\.json$")
                pattern.matcher(fileName).matches() && fileName != excludeFile
            }

            // Iterate through the JSON files and delete them
            jsonFiles?.forEach { file ->
                if (file.delete()) {
                    //println("Deleted file: ${file.name}")
                } else {
                    //println("Failed to delete file: ${file.name}")
                }
            }
        } else {
            Log.e("File deletion error", "Directory does not exist or is not a directory")
        }
    }

    fun deleteCSVFiles()
    {
        val appInternalDir = context.getFilesDir()
        val dataSubDir = File(appInternalDir, "Data")
        val excludeFile = "${Helper().getLocalUserName(applicationContext)}.csv"

        if (dataSubDir.exists() && dataSubDir.isDirectory) {
            val csvFiles = dataSubDir.listFiles { _, fileName ->
                // --- same as function above, deletes all the csv files with a specification
                val pattern = Pattern.compile("^[0-9]\\.csv$")
                pattern.matcher(fileName).matches() && fileName != excludeFile
            }

            // Iterate through the specified files and delete them
            csvFiles?.forEach { file ->
                if (file.delete()) {
                    //println("Deleted file: ${file.name}")
                } else {
                    //println("Failed to delete file: ${file.name}")
                }
            }
        } else {
            Log.e("File deletion error", "Directory does not exist or is not a directory")
        }



    }

    fun updateFiles()
    {
        val py = Python.getInstance()
        val module = py.getModule("json_to_csv")
        val module2 = py.getModule("dataScript2")



        val appInternalDir = context.getFilesDir()
        if (appInternalDir.exists() && appInternalDir.isDirectory) {
            val jsonFiles = appInternalDir.listFiles { _, fileName ->
                // Define a regular expression pattern to match single-digit number JSON files
                val pattern = Pattern.compile("^[0-9]\\.json$")
                pattern.matcher(fileName).matches()
            }

            // Iterate through the JSON files and run the python modules on them;
            val processedFilenames = mutableListOf<String>()

            jsonFiles?.forEach { jsonFile ->
                module.callAttr("main", jsonFile.name)
                // Add the filename to the list
                processedFilenames.add(jsonFile.name)
            }

            val csvFilenames = processedFilenames.map { filename ->
                val csvFilename = filename.replace(".json", ".csv")
                module2.callAttr("main", csvFilename)
                csvFilename // Add the modified filename to the list
            }
        } else {
            Log.e("File error", "Directory does not exist or is not a directory")
        }


    }

    fun sendPhotos2()
    {
        // sends a photo to the first phone in the list
        if (links.isNotEmpty() && links[0].isNotEmpty()) {
            val endpoint1: String = links[0][0]
            //val usernumber1: String = links[0][1]

            sendPhoto(endpoint1)
        } else {
            // we didn't have one connection
        }


    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        stopDiscovery()
        links.clear()
        lost.clear()
        offline.clear()
    }

}
