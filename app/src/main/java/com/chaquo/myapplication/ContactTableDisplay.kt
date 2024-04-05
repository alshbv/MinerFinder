package com.chaquo.myapplication

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.io.IOException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform


class ContactDisplay : AppCompatActivity() {

    private val TAG = "Contact"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.title = "Contact Graph"

        setContentView(R.layout.activity_contact)

        // plan:
        // store all the data in a folder, read everything into it

        val filesDir1 = applicationContext.filesDir
        val subDirectoryName = "Contact_Data"
        val subDirectory = File(filesDir1, subDirectoryName)
        if (!subDirectory.exists()) {
            subDirectory.mkdirs()
        }

        // dummy files (to be replaced with actual data)
        val data1 = listOf(
            listOf("ID", "Location", "Time"),
            listOf("1", "B", "2-3"),
            listOf("1", "C", "3-4"),
            listOf("1", "A", "4-5"),
        )

        val data2 = listOf(
            listOf("ID", "Location", "Time"),
            listOf("2", "B", "2-3"),
            listOf("2", "B", "3-4"),
            listOf("2", "A", "4-5"),
        )

        saveCsvToFile("1.csv", data1)
        saveCsvToFile("2.csv", data2)


        // starts python code
        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val py = Python.getInstance()
        val module = py.getModule("contactTableBuilder")
        val resultObj = module.callAttr("find_duplicate_entries", subDirectoryName)
        val resultString: String = resultObj.toJava(String::class.java)

        fillTableLayout(this, resultString)

    }

    private fun saveCsvToFile(fileName: String, data: List<List<String>>) {
        try {
            val directory = File(filesDir, "Contact_Data")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, fileName)
            val fileWriter = FileWriter(file)

            // Write the data to the CSV file
            for (line in data) {
                fileWriter.write(line.joinToString(","))
                fileWriter.write("\n")
            }

            fileWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun fillTableLayout(context: Context, fileContents: String) {

        val tableLayout: TableLayout = findViewById(R.id.routingTable)
        val rows = fileContents.trim().split("\n")

        for ((index, row) in rows.withIndex()) {
            val columns = row.trim().split(",(?![^()]*\\))".toRegex())
            val tableRow = TableRow(context)

            for (column in columns) {
                val textView = TextView(context)
                textView.text = column
                textView.setPadding(8, 8, 8, 8)
                if (index != 0) {
                    textView.setTextColor(Color.DKGRAY)
                } else {
                    textView.setTextColor(Color.LTGRAY)
                }

                if (column.contains("-")) {
                    Log.d(TAG, column[0].toString())
                    var result = column

                    // Check if the first letter is '0', and if so, remove it
                    if (result.isNotEmpty() && result[0] == '0') {
                        result = result.substring(1)
                    }

                    // Split the string by the first "-"
                    val index = result.indexOf("-")
                    if (index != -1) {
                        val fragment1 = result.substring(0, index)
                        val fragment2 = result.substring(index + 1)

                        // Typecast the fragments as integers and compare them
                        val num1 = fragment1.toIntOrNull() ?: 0
                        val num2 = fragment2.toIntOrNull() ?: 0

                        // Swap the fragments if the first is greater than the second
                        if (num1 > num2) {
                            result = "$fragment2-$fragment1"
                            textView.text = result
                        }
                    }

                }


                if (column.contains(Regex("\\(\\s*|\\s*\\)"))) {
                    val replaceColumn = column.replace(Regex("[^a-zA-Z]"), "")
                    //note: this assumes that all locations are represented by a single letter!
                    //if (replaceColumn.isNotEmpty()) {
                    val charAtZero = if (replaceColumn.isNotEmpty()) replaceColumn[0] else ' '
                    val charAtOne = if (replaceColumn.isNotEmpty()) replaceColumn[1] else ' '
                    val dottedColumn = charAtZero + "-" + charAtOne
                    textView.text = dottedColumn
                    //}
                    //textView.text = replaceColumn
                }

                val layoutParams = TableRow.LayoutParams(
                    TableLayout.LayoutParams.WRAP_CONTENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                textView.layoutParams = layoutParams

                // adds a colored background drawable, with borders, for each row
                val drawable = GradientDrawable()
                if (index != 0) {
                    drawable.setColor(Color.WHITE)
                }
                drawable.setStroke(1, Color.BLACK)
                drawable.setCornerRadii(floatArrayOf(1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f))
                textView.background = drawable

                tableRow.addView(textView)
            }

            if (index == 0) {
                tableRow.setBackgroundColor(Color.rgb(30, 100, 200))
            } else {
                tableRow.setBackgroundColor(Color.WHITE)
            }

            tableLayout.addView(tableRow)
        }
    }




    }
