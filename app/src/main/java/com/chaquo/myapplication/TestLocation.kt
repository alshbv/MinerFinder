package com.chaquo.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class TestLocation : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_location)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        val textView: TextView = findViewById(R.id.textView)
        val button1: Button = findViewById(R.id.buttonA)
        val button2: Button = findViewById(R.id.buttonB)
        val button3: Button = findViewById(R.id.buttonC)
        val button4: Button = findViewById(R.id.buttonD)
        val buttonU: Button = findViewById(R.id.buttonU)

        // Set onClickListeners for the buttons (add functionality as needed)
        button1.setOnClickListener {
            // Handle button 1 click
            val intent = Intent(StepCounter.ACTION_SET_PILLAR)
            intent.putExtra(StepCounter.EXTRA_PILLAR_VALUE, "A")
            sendBroadcast(intent)

        }

        button2.setOnClickListener {
            val intent = Intent(StepCounter.ACTION_SET_PILLAR)
            intent.putExtra(StepCounter.EXTRA_PILLAR_VALUE, "B")
            sendBroadcast(intent)

        }

        button3.setOnClickListener {
            val intent = Intent(StepCounter.ACTION_SET_PILLAR)
            intent.putExtra(StepCounter.EXTRA_PILLAR_VALUE, "C")
            sendBroadcast(intent)

        }

        button4.setOnClickListener {
            val intent = Intent(StepCounter.ACTION_SET_PILLAR)
            intent.putExtra(StepCounter.EXTRA_PILLAR_VALUE, "D")
            sendBroadcast(intent)
        }

        buttonU.setOnClickListener {
            // Read last 10 lines from 7.json file
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

            // Get the last 10 lines from the list
            val last10Lines = lines.takeLast(10)
            var output = ""
            for (line1 in last10Lines) {
                output = output + line1 + "\n"
            }

            textView.text = output.toString()

        }

    }

    private fun updateText()
    {
    }
}
