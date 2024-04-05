package com.chaquo.myapplication

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.myapplication.db.StoredImageData

class ImageTagAdapter(private val context: Context) :
    RecyclerView.Adapter<ImageTagAdapter.ViewHolder>() {

    private var itemList: List<StoredImageData> = emptyList()

    // ViewHolder class
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val tagTextView: TextView = itemView.findViewById(R.id.tagTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gallery_item_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageTag = itemList[position]

        // Load the image using an image loading library (e.g., Glide or Picasso)
        //Glide.with(context)
        //    .load(Uri.parse(imageTag.imageUri))
        //    .into(holder.imageView)

        holder.tagTextView.text = imageTag.tags
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun setData(newItemList: List<StoredImageData>) {
        itemList = newItemList
        notifyDataSetChanged()
    }
}
