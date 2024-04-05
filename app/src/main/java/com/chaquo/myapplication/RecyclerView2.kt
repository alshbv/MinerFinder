package com.chaquo.myapplication

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.TraceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.FileWriter
import java.io.IOException



class RecyclerView2 : AppCompatActivity() {

    private val TAG = "NavigationView"
    private lateinit var adapter: item_extender
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        //setSupportActionBar(toolbar)
        supportActionBar?.title = "Miner Prediction Results"


        setContentView(R.layout.recycler_view_2)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        //val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView = findViewById(R.id.recyclerView)
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        val TAG = "model"
        Log.d(TAG, "adding the filenames to the list")

        val filesDir1 = applicationContext.filesDir
        val subDirectoryName = "Results"
        val subDirectory = File(filesDir1, subDirectoryName)
        if (!subDirectory.exists()) {
            subDirectory.mkdirs() // Create the subdirectory if it doesn't exist
        }

        val csvFiles = subDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".csv") && file.name != ".csv"
        }.sortedBy { it.name }


        val itemList: MutableList<FileItem> = mutableListOf()
        val minerList: MutableList<String> = mutableListOf()

        for (file in csvFiles) {

            val filePath1 = File(subDirectory, file.name).absolutePath
            val fileData = readCSVFile(filePath1)

            val lines = fileData.trim().split("\n")
            val firstColumnValues: List<String> = lines.map { it.split(",")[0] }

            // get the first value in the first column:
            val firstValueInFirstColumn = firstColumnValues.getOrNull(1)
            val stringName = "miner " + firstValueInFirstColumn.toString()
            val fileItem = FileItem(stringName, fileData)

            if (firstValueInFirstColumn != null) {
                minerList.add(firstValueInFirstColumn.toString())
            }

            if (fileItem != null) {
                itemList.add(fileItem)
            }
        }


        recyclerView.layoutManager = layoutManager

        val itemList2: MutableList<FileItem> = mutableListOf()
        val adapter = item_extender(this, itemList2, object : item_extender.OnItemClickListener {
            override fun onItemClick(item: FileItem) {
                // Handle item click event here, you can access the selected FileItem object


            }
        })

        recyclerView.adapter = adapter


        val run_model = findViewById<Button>(R.id.run_model)
        run_model.setOnClickListener {
            runModel("miner7.csv", this)
        }

        val measure_battery = findViewById<Button>(R.id.measure_battery)
        measure_battery.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                val result = measureBatteryConsumption(this@RecyclerView2, 50)
                // Handle the result if needed
            }
        }



    }


    private fun readCSVFile(filePath: String): String {
        try {
            val file1 = FileInputStream(filePath)
            val reader1 = InputStreamReader(file1)
            val buffread1 = BufferedReader(reader1)
            val sb = StringBuilder()
            var line: String?
            while (buffread1.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            buffread1.close()
            return sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    private fun runModel(fileName: String, context: Context) {

        TraceCompat.beginSection("MyFunction")

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        val py = Python.getInstance()

        val module = py.getModule("plotScriptable")

        val list_of_files = mutableListOf<String>("3.csv","4.csv", "5.csv")

        for (a in list_of_files)
        {
            module.callAttr("mainScriptable", a)
        }
        //module.callAttr("mainScriptable", "day1.csv")

        runOnUiThread()
        {
            populateTable(context)
        }
        //

        TraceCompat.endSection()


    }

    private fun populateTable(context: Context) {
        val TAG = "model"
        Log.d(TAG, "adding the filenames to the list")

        val filesDir1 = context.filesDir
        val subDirectoryName = "Results"
        val subDirectory = File(filesDir1, subDirectoryName)
        if (!subDirectory.exists()) {
            subDirectory.mkdirs() // Create the subdirectory if it doesn't exist
        }

        val csvFiles = subDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".csv") && file.name != ".csv"
        }.sortedBy { it.name }


        val itemList: MutableList<FileItem> = mutableListOf()
        val minerList: MutableList<String> = mutableListOf()

        for (file in csvFiles) {

            val filePath1 = File(subDirectory, file.name).absolutePath
            val fileData = readCSVFile(filePath1)

            val lines = fileData.trim().split("\n")
            val firstColumnValues: List<String> = lines.map { it.split(",")[0] }

            // get the first value in the first column:
            val firstValueInFirstColumn = firstColumnValues.getOrNull(1)
            val stringName = "miner " + firstValueInFirstColumn.toString()
            val fileItem = FileItem(stringName, fileData)

            if (firstValueInFirstColumn != null) {
                minerList.add(firstValueInFirstColumn.toString())
            }

            if (fileItem != null) {
                itemList.add(fileItem)
            }
        }


        // also add the list of miners to a display
        //val minerListR: TextView = context.findViewById(R.id.minerListResults)
        //minerListR.text = "Miners tracked: " + minerList.joinToString(" ,")

        val adapter = item_extender(context, itemList, object : item_extender.OnItemClickListener {
            override fun onItemClick(item: FileItem) {
                // Handle item click event here, you can access the selected FileItem object

            }
        })

        recyclerView.adapter = adapter


    }

    fun measureBatteryConsumption(
        context: Context,
        numberOfExecutions: Int
    ): Int {
        // Get initial battery level
        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val initialBatteryLevel =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // Run the function
        val startTime = SystemClock.elapsedRealtime()
        for (i in 0 until numberOfExecutions) {
            runModel("miner7.csv", this)
        }
        // note that this is in milliseconds!
        val elapsedTime = SystemClock.elapsedRealtime() - startTime



        // Get final battery level
        val finalBatteryLevel =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        // wait for elapsedTime
        val initialBatteryLevel_noModel =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        SystemClock.sleep(elapsedTime)

        val finalBatteryLevel_noModel =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)


        // Calculate battery consumption

        runOnUiThread {

            val minerListR: TextView = this.findViewById(R.id.minerListResults)
            minerListR.text =
                "Initial Charge: " + initialBatteryLevel.toString() + "\n" + "Final Charge: " + finalBatteryLevel.toString()


            // enter into a .txt file for reporting purposes
            try {
                val file = File(context.filesDir, "batteryReport.txt")
                val writer = BufferedWriter(FileWriter(file, true))
                writer.append("REPORT\n    TIME: $elapsedTime (IN MILLIS)\n")
                writer.append("WITH MODEL, EXECUTED $numberOfExecutions TIMES: \n")
                writer.append("INITIAL BATTERY: ${initialBatteryLevel} \n")
                writer.append("FINAL BATTERY: ${finalBatteryLevel} \n")
                writer.append("CONTROL: \n")
                writer.append("INITIAL BATTERY: ${initialBatteryLevel_noModel} \n")
                writer.append("FINAL BATTERY: ${finalBatteryLevel_noModel} \n")
                writer.append("NOTE: (change in battery level) * Battery Capacity * Voltage = energy consumed (typically in mWatt-Hours)")
                writer.append("Check capacity externally, voltage is typically 3.7V")
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return initialBatteryLevel - finalBatteryLevel


    }

}
private fun testing(context: Context) {
    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus: Intent = context.registerReceiver(null, filter)!!

    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

    //val batteryPercentage = level / scale.toFloat() * 100

}


