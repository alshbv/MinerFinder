package com.chaquo.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class item_extender(private val context: Context, private val itemList: List<FileItem>, private val onItemClickListener: OnItemClickListener) : RecyclerView.Adapter<item_holder>() {

    interface OnItemClickListener {
        fun onItemClick(item: FileItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): item_holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.user_item_layout, parent, false)
        return item_holder(view)
    }

    override fun onBindViewHolder(holder: item_holder, position: Int) {
        val currentItemName = itemList[position].name
        val currentItemData = itemList[position].data

        val name1 = Helper().getLocalUserName(context)


        // Bind data to the views within the ViewHolder
        // fill the textView
        // Clear the table layout
        holder.clearTableLayout()
        // Fill the table with the CSV data
        holder.fillTableLayout(context, currentItemData)

        holder.fillName(currentItemName)




        holder.itemView.setOnClickListener {
            onItemClickListener.onItemClick(itemList[position])

        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }



}

data class FileItem(val name: String, val data: String)