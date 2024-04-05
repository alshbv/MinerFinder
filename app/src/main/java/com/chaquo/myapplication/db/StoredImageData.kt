package com.chaquo.myapplication.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stored_image_data_table")
data class StoredImageData(
    @PrimaryKey(autoGenerate = true) var id: Int = 0, // Primary key

    @ColumnInfo(name = "image_id") val imageid: String?,

    @ColumnInfo(name = "wasReceived") val is_received: Boolean = false,

    @ColumnInfo(name = "path") val imgpath: String?,

    @ColumnInfo(name = "TAGS") var tags: String? = ""

    )