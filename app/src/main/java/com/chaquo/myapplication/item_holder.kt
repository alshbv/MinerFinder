package com.chaquo.myapplication

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class item_holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val textViewItem: TextView = itemView.findViewById(R.id.textViewItem)
    private val tableLayout: TableLayout = itemView.findViewById(R.id.tableLayout1)
    private val TAG = "Item Holder"

    fun clearTableLayout() {
        // Clear the existing rows from the table layout
        tableLayout.removeAllViews()
    }

    fun fillTableLayout(context: Context, fileContents: String) {
        val rows = fileContents.trim().split("\n")

        for ((index, row) in rows.withIndex()) {
            val columns = row.trim().split(",(?![^()]*\\))".toRegex())
            val tableRow = TableRow(context)

            for (column in columns) {
                val textView = TextView(context)
                textView.text = column
                textView.setPadding(8, 8, 8, 8)
                if(index != 0)
                {
                    textView.setTextColor(Color.DKGRAY)
                }
                else
                {
                    textView.setTextColor(Color.LTGRAY)
                }

                if(column.contains("-"))
                {
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

                        // also if the first is one
                        if (num1 == 1) {
                            result = "$fragment2-$fragment1"
                            textView.text = result
                        }
                    }

                }


                if(column.contains(Regex("\\(\\s*|\\s*\\)")))
                {
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
                if(index != 0)
                {
                    drawable.setColor(Color.WHITE)
                }
                drawable.setStroke(1, Color.BLACK)
                drawable.setCornerRadii(floatArrayOf(1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f))
                textView.background = drawable

                tableRow.addView(textView)
            }

            if(index == 0)
            {
                tableRow.setBackgroundColor(Color.rgb(30, 100, 200))
            }
            else
            {
                tableRow.setBackgroundColor(Color.WHITE)
            }

            tableLayout.addView(tableRow)
        }

    }


    fun fillName(minerID: String) {
        textViewItem.text = minerID

    }
}
