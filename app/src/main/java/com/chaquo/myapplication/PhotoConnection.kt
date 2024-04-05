package com.chaquo.myapplication


import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SimpleArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.chaquo.myapplication.databinding.ActivityConnectionBinding
import com.chaquo.myapplication.databinding.ActivityPhotoConnectionBinding
import com.chaquo.myapplication.db.AppDatabase
import com.chaquo.myapplication.db.StoredImageData
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale


class PhotoConnection : AppCompatActivity() {
    private val TAG = "Connection"
    private val SERVICE_ID = "Nearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    private var isAdvertising: Boolean = false
    private var isDiscovering: Boolean = false
    private var eid : String = ""

    private val READ_REQUEST_CODE = 42
    private val ENDPOINT_ID_EXTRA = "com.foo.myapp.EndpointId"

    private val links = mutableListOf<List<String>>() // endpointid, usernumber
    private val connectedEndpoints = mutableListOf<String>()
    private val connectedEndpoints2 = mutableListOf<List<String>>() // endpointID, usernumber

    companion object {
        private const val LOCATION_PERMISSION_CODE = 100
        private const val READ_PERMISSION_CODE = 101
        private const val REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 1
        private const val REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 2
        private const val BLUETOOTH_PERMISSION = 4
        private const val BLUETOOTH_ADMIN_PERMISSION = 5
        private const val BLUETOOTH_ADVERTISE_PERMISSION = 6
        private const val BLUETOOTH_CONNECT_PERMISSION = 7
        private const val BLUETOOTH_SCAN_PERMISSION = 8
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        stopDiscovery()
        links.clear()
        connectedEndpoints.clear()
        connectedEndpoints2.clear()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PHOTOCONNECTION2", "PHOTOCONNECTION2")

        supportActionBar?.title = "Image Connection"
        setContentView(R.layout.activity_photo_connection)

        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE)
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION)
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION)
        checkPermission(Manifest.permission.BLUETOOTH, BLUETOOTH_PERMISSION)
        checkPermission(Manifest.permission.BLUETOOTH_ADMIN, BLUETOOTH_ADMIN_PERMISSION)
        checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE, BLUETOOTH_ADVERTISE_PERMISSION)
        checkPermission(Manifest.permission.BLUETOOTH_CONNECT, BLUETOOTH_CONNECT_PERMISSION)
        checkPermission(Manifest.permission.BLUETOOTH_SCAN, BLUETOOTH_SCAN_PERMISSION)




        val viewBinding = ActivityPhotoConnectionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.offButton2.setOnClickListener { modeOff() }
        viewBinding.onButton2.setOnClickListener { modeOn() }
        viewBinding.makeConnectionButton.setOnClickListener{ showEndpointDialog()}

        stopAdvertising()
        stopDiscovery()

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission has been granted, do something
                    Log.d("perm", "got write permission")
                } else {
                    // Permission has been denied, show a message or disable functionality
                    Log.d("perm", "filed to get write permission")
                }
                return
            }
            7 ->{
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission has been granted, do something
                    Log.d("perm", "got Connect permission")
                } else {
                    // Permission has been denied, show a message or disable functionality
                    Log.d("perm", "filed to get Connect permission")
                }
                return
            }
        }
    }


    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this,permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            Log.d("PERMISSION_CHECK", "denied $permission")
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
        else {
            Log.d("PERMISSION_CHECK", "$permission granted")
        }
    }

    private fun startAdvertising() {
        val advertisingOptions: AdvertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        //val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)

        val endpointName = Helper().getLocalUserName(applicationContext)
        Nearby.getConnectionsClient(context)
            .startAdvertising(
                endpointName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            )
            .addOnSuccessListener { unused: Void? ->
                //connectionReport.text = "Advertising as " + getLocalUserName()
                Log.d("Advertising as ", endpointName)
                this.isAdvertising = true
            }
            .addOnFailureListener { e: Exception? -> }
    }

    private fun startDiscovery() {
        val discoveryOptions: DiscoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        //val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)

        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                //connectionReport.text = "Discovering"
                this.isDiscovering = true
            }
            .addOnFailureListener { e: java.lang.Exception? -> }
    }

    private fun stopAdvertising() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        this.isAdvertising = false
    }

    private fun stopDiscovery() {
        Nearby.getConnectionsClient(context).stopDiscovery()
        this.isDiscovering = false
    }

    private fun modeOff()
    {
        if(isAdvertising)
            stopAdvertising()
        if (isDiscovering)
            stopDiscovery()

        //
        //Log.d("ENDPOINT: ", )
        links.clear()

        for (endpointId in connectedEndpoints) {
            // Check if the endpoint is still connected
            Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId)
        }
        // Clear the list of connected endpoints
        connectedEndpoints.clear()

        connectedEndpoints2.clear()

        val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
        connectionReport.text = "Stopped searching for endpoints"


    }

    private fun modeOn()
    {
        if(!isAdvertising) {
            startAdvertising()
            val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
            connectionReport.text = "Searching"
        }
        if (!isDiscovering) {
            startDiscovery()
        }


    }

    /**
     * Fires an intent to spin up the file chooser UI and select an image for sending to endpointId.
     */
    private fun showImageChooser(endpointId: String) {
        this.eid = endpointId
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        intent.putExtra(ENDPOINT_ID_EXTRA, endpointId)
        startActivityForResult(intent, READ_REQUEST_CODE)
        Log.d(TAG, "end img")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK && resultData != null) {
//            val endpointId = resultData.getStringExtra(ENDPOINT_ID_EXTRA)
            val endpointId = this.eid
            Log.d("EID", endpointId.toString())

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
            val filenameMessage = filePayload.id.toString() + ":" + uri.lastPathSegment

            Log.d("FILENAME", filenameMessage)

            // Send the filename message as a bytes payload.
            val filenameBytesPayload =
                Payload.fromBytes(filenameMessage.toByteArray(StandardCharsets.UTF_8))
            Nearby.getConnectionsClient(context).sendPayload(endpointId!!, filenameBytesPayload)

            // Finally, send the file payload.
            if (endpointId != null) {
                Log.d(TAG, "in result")

                Nearby.getConnectionsClient(context).sendPayload(endpointId, filePayload)
                    .addOnSuccessListener {
                        Log.d(TAG, "successful send?")
                    }
            }
        }
    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // An endpoint was found. We request a connection to it.
                Nearby.getConnectionsClient(context)
                .requestConnection(Helper().getLocalUserName(applicationContext), endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(
                    OnSuccessListener { unused: Void? -> })
                .addOnFailureListener(
                    OnFailureListener { e: java.lang.Exception? -> })

                val discoveredEndpointName = info.endpointName
                val discoveredEndpointID = endpointId
                links.add(listOf(discoveredEndpointID, discoveredEndpointName))
                // display the potential endpoints
                //val linksDisplay: TextView = findViewById<TextView>(R.id.connection_report)
                //val linksNumbers = links.map { it[1] }
                //linksDisplay.text = "Potential Endpoints: $linksNumbers"

            }

            override fun onEndpointLost(endpointId: String) {

                // A previously discovered endpoint has gone away.
                if (VERSION.SDK_INT >= VERSION_CODES.N) {
                    links.removeIf { it[0] == endpointId }
                }

                // Update the display of potential endpoints
                //val linksDisplay: TextView = findViewById<TextView>(R.id.connection_report)
                //val linksNumbers = links.map { it[1] }
                //linksDisplay.text = "An endpoint was lost! Potential Endpoints: $linksNumbers"

            }
        }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {

            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Automatically accept the connection on both sides.
                Log.d("CONINFO", connectionInfo.toString())
                Log.d("CONINFO", endpointId.toString())
                Log.d("CONINFO", context.toString())

                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)

                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        connectionReport.text = "Connected"

                        connectedEndpoints.add(endpointId)
                        val endpointData = links.find { it[0] == endpointId }
                        if (endpointData != null) {
                            connectedEndpoints2.add(endpointData)
                        }

                        val linksDisplay: TextView = findViewById<TextView>(R.id.connection_report)
                        val connectedNumbers = connectedEndpoints2.map { it[1] }
                        linksDisplay.text = "Potential Endpoints: $connectedNumbers"


                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {}
                    ConnectionsStatusCodes.STATUS_ERROR -> {}
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received
                // this is alright because we can only have one connection at a time
                //Log.d("DISCONNECTED", "start disconnecting")
                //val connectionReport: TextView = findViewById<TextView>(R.id.connection_report)
                //connectionReport.text = "Searching"
                if (VERSION.SDK_INT >= VERSION_CODES.N) {
                    connectedEndpoints2.removeIf { it[0] == endpointId }
                    connectedEndpoints.removeIf{it == endpointId}

                }
                val linksDisplay: TextView = findViewById<TextView>(R.id.connection_report)
                val connectedNumbers = connectedEndpoints2.map { it[1] }
                linksDisplay.text = "Potential Endpoints: $connectedNumbers"

            }
        }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        //        private val context: Context? = null
        private var incomingFilePayloads = SimpleArrayMap<Long, Payload>()
        private var completedFilePayloads = SimpleArrayMap<Long, Payload>()
        private var filePayloadFilenames = SimpleArrayMap<Long, String>()
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // A new payload is being sent over.
            Log.d(TAG, "Payload Received")
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val payloadFilenameMessage = String(payload.asBytes()!!, StandardCharsets.UTF_8)
                    val payloadId: Long = addPayloadFilename(payloadFilenameMessage)
                    processFilePayload(payloadId)
                    Log.d(TAG, payload.asBytes().toString())
                    val dataDisplay: TextView = findViewById<TextView>(R.id.data_received)
                    dataDisplay.text = payloadFilenameMessage
                }
                Payload.Type.FILE -> {
                    Log.d(TAG, "receiving file?")
                    // Add this to our tracking map, so that we can retrieve the payload later.
                    incomingFilePayloads.put(payload.id, payload);

                    val fileUri = payload.asFile()!!.asUri()
                    Log.d("saveimage", fileUri.toString())

                }
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is payloadId:filename.
         */
        private fun addPayloadFilename(payloadFilenameMessage: String): Long {
            Log.d("PATH", "IN ADDPAYFIL")
            val parts = payloadFilenameMessage.split(":").toTypedArray()
            val payloadId = parts[0].toLong()
            val filename = parts[1]
            filePayloadFilenames.put(payloadId, filename)
            return payloadId
        }

        private fun copyStream(`in`: InputStream?, out: OutputStream) {
            try {
                val buffer = ByteArray(1024*4)
                var read: Int
                while (`in`!!.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.flush()
            } finally {
                `in`!!.close()
                out.close()
            }
        }

        private fun processFilePayload(payloadId: Long) {
            Log.d("PATH", "IN PROCFILE")
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            val filePayload = completedFilePayloads[payloadId]
            val filename: String? = filePayloadFilenames.get(payloadId)
            if(filename != null)
                Log.d("PFP", filename)
            if (filePayload != null && filename != null) {
                completedFilePayloads.remove(payloadId)
                filePayloadFilenames.remove(payloadId)

                //Log.d("DOWN", "ABOVE REMOVE DOWN")

                // Get the received file (which will be in the Downloads folder)
                // Because of https://developer.android.com/preview/privacy/scoped-storage, we are not
                // allowed to access filepaths from another process directly. Instead, we must open the
                // uri using our ContentResolver.
                val uri: Uri? = filePayload.asFile()!!.asUri()

                lateinit var imageView: ImageView
                imageView = findViewById(R.id.imageView)
                imageView.setImageURI(uri)

                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(applicationContext, "Image Received!", duration)
                toast.show()


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
                    Log.d("image1", MediaStore.Images.Media.RELATIVE_PATH)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Received-Images-MinerFinder")
                }
            }


            val contentResolver = context.contentResolver
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            if (imageUri != null) {
                val outputStream = contentResolver.openOutputStream(imageUri)
                val inputStream = uri?.let { context.contentResolver.openInputStream(it) }
                if (inputStream != null && outputStream != null) {
                    try {
                        inputStream.copyTo(outputStream)
                        Log.d(TAG, "Image finally received!")
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

            val imageData = StoredImageData(imageid = name, is_received = true, imgpath = imageUri.toString(), tags = "Received")
            db().storedImageDao().insert(imageData)


        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d("PATH", "ST SUCC")

                val payloadId = update.payloadId
                val payload = incomingFilePayloads.remove(payloadId)
                completedFilePayloads.put(payloadId, payload)

                Log.d("PATH", completedFilePayloads.toString())

                if (payload != null && payload.type == Payload.Type.FILE) {
                    processFilePayload(payloadId)
                }
            }
            if(update.status == PayloadTransferUpdate.Status.IN_PROGRESS) {
                Log.d("PATH", "ST IN PROGRESS")
            }
            if(update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.d("PATH", "ST FAILURE")
            }
            if(update.status == PayloadTransferUpdate.Status.CANCELED) {
                Log.d("PATH", "ST CANCELLED")
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

    // obsolete
    private fun makeConnection(endpointId: String)
    {
        // should use a list
        // this needs a stress test for if we are ACTUALLY connecting or not
        Log.d("ENDPOINT_CONNECT", endpointId)
        if (links.isNotEmpty() && links[0].isNotEmpty()) {

            //val endpointId = links[0][0]

            // An endpoint was found. We request a connection to it.
            Nearby.getConnectionsClient(context)
                .requestConnection(Helper().getLocalUserName(applicationContext), endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(
                    OnSuccessListener { unused: Void? ->
                        connectedEndpoints.add(endpointId)
                        val endpointData = links.find { it[0] == endpointId }
                        if (endpointData != null) {
                            connectedEndpoints2.add(endpointData)
                        }

                    })
                .addOnFailureListener(
                    OnFailureListener { e: java.lang.Exception? -> })
        }
        else
        {
            Log.d(TAG,"No endpoints were found!")
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(applicationContext, "No endpoints were found", duration)
            toast.show()


        }

    }

    fun showEndpointDialog() {
        if(links.isNotEmpty()) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select Endpoint")

            //builder.setMessage("Select an endpoint to connect to:")
            val endpointList = connectedEndpoints2.map { it[1] }

            // this is only good when we keep the current custom
            // of naming endpoints by numbers
            val sortedEndpointList = endpointList.map { it.toInt() }
                .sorted()
                .map { it.toString() }


            val endpointNames = endpointList.toTypedArray()
            var selectedItem: Int = 0

            builder.setSingleChoiceItems(endpointNames, 0,
                DialogInterface.OnClickListener { dialog, which ->
                    selectedItem = which

                    val selectedEndpoint = endpointList[selectedItem]
                    // send to the selected endpoint here
                    Log.d("ENDPOINT_SELECTION_NAME", selectedEndpoint)
                    val toSend = connectedEndpoints2.find { it[1] == selectedEndpoint }
                    val targetEndpoint = toSend?.get(0).toString()
                    Log.d("ENDPOINT_SELECTION_ID", targetEndpoint)

                    for (innerList in connectedEndpoints2)
                    {
                        Log.d("ENDPOINT_SELECTION_LIST", innerList.joinToString(", "))
                    }
                })

            builder.setPositiveButton(
                "OK"
            ) { dialog, which ->
                //when user clicks OK
                val selectedEndpoint = endpointList[selectedItem]
                // send to the selected endpoint here
                val toSend = connectedEndpoints2.find { it[1] == selectedEndpoint }
                val targetEndpoint = toSend?.get(0).toString()
                if(targetEndpoint != null)
                {
                    Log.d("ENDPOINT_CONNECTED_TO", targetEndpoint)
                    //makeConnection(targetEndpoint)
                    showImageChooser(targetEndpoint)
                }

            }



            builder.setNegativeButton("Cancel") { dialog, which ->
                // Handle cancel action if needed
            }

            val dialog = builder.create()
            dialog.show()
        }
    }


    private fun db(): AppDatabase {
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
    }

}