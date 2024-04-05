package com.chaquo.myapplication

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.chaquo.myapplication.db.AppDatabase
import com.chaquo.myapplication.db.StoredImageData
import java.io.File

class Photos : AppCompatActivity() {
    //lateinit var imageView: ImageView
    lateinit var button: Button
    private val pickImage = 100
    private var imageUri: Uri? = null


    lateinit var currentImageData: StoredImageData




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_tag)

        val pick_image_button = findViewById<Button>(R.id.button1_tag)
        val load_tag = findViewById<Button>(R.id.button2_tag)
        val add_tag = findViewById<Button>(R.id.button3_tag)
        var image_display: ImageView = findViewById(R.id.imageView_tag)
        var tag_display: TextView = findViewById(R.id.currentTags_tag)


        pick_image_button.setOnClickListener{loadImage()}
        load_tag.setOnClickListener{displayTag()}
        add_tag.setOnClickListener{enterTag()}



        //button.setOnClickListener {
            //val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            //startActivityForResult(gallery, pickImage)

            //val galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
            //galleryIntent.type = "image/*"
            //galleryIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            //galleryIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(folder))
            //startActivityForResult(galleryIntent, pickImage)

            //openCustomImagePicker()
        //    pickImageFolder()

        //}


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == pickImage) {
            imageUri = data?.data
            //imageView.setImageURI(imageUri)
        }
    }

    private fun pickImageFolder(){
        val images = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("Pictures/MinerFinder-Image")

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val imageId = it.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(queryUri, imageId)
                images.add(contentUri)
            }
        }
    }

    private fun loadImage()
    {

        // for now, picks the first image if the table in DB is not empty
        if(db().storedImageDao().isTableNotEmpty())
        {
            var image_display: ImageView = findViewById(R.id.imageView_tag)
            var tag_display: TextView = findViewById(R.id.currentTags_tag)

            // assert that the data won't be a null
            currentImageData = db().storedImageDao().getFirstStoredData()!!
            if(currentImageData != null)
            {
                val imageuri = Uri.parse(currentImageData.imgpath)
                image_display.setImageURI(imageuri)
                tag_display.text = currentImageData.tags
            }
        }

    }

    private fun enterTag()
    {
        if(currentImageData != null)
        {
            var tag_by_user = findViewById<EditText>(R.id.tagEntry_tag)
            // tags are separated by semicolons for now
            val tagValue = ";" + tag_by_user.text.toString()

            if(tag_by_user.text != null)
            {
                db().storedImageDao().updateTags(currentImageData.id, tagValue)
            }
        }

    }


    // ideally, this should run on a thread
    // so we can keep updating the tags
    private fun displayTag()
    {
        var image_display: ImageView = findViewById(R.id.imageView_tag)
        var tag_display: TextView = findViewById(R.id.currentTags_tag)

        if(currentImageData != null)
        {
            tag_display.text = currentImageData.tags
        }
    }

    private fun db(): AppDatabase {
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
    }

}