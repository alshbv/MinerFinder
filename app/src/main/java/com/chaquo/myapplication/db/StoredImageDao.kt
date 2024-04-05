package com.chaquo.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update


@Dao
interface StoredImageDao {
    @Insert
    fun insert(image1: StoredImageData)

    @Update
    fun update(image1: StoredImageData)

    @Query("SELECT * from stored_image_data_table")
    fun getAllImages() : List<StoredImageData>?

    @Query("SELECT * from stored_image_data_table where wasReceived = 'false'")
    fun getOwnImages() : List<StoredImageData>?

    @Query("SELECT * from stored_image_data_table where wasReceived = 'true'")
    fun getCollectedImages() : List<StoredImageData>?

    @Query("SELECT * from stored_image_data_table WHERE image_id = :imageid LIMIT 1" )
    fun getImageById(imageid: String) : StoredImageData?

    @Query("Delete from stored_image_data_table WHERE image_id = :imageid" )
    fun deleteImageById(imageid: String)

    @Query("DELETE FROM stored_image_data_table")
    fun clear()

    @Query("UPDATE stored_image_data_table SET wasReceived = :is_in WHERE id = :userId")
    open fun noteAsCaptured(userId: Int, is_in: Boolean = true)

    @Query("SELECT * FROM stored_image_data_table ORDER BY id DESC LIMIT 1")
    fun getFirstStoredData(): StoredImageData?

    @Query("SELECT EXISTS (SELECT 1 FROM stored_image_data_table LIMIT 1)")
    fun isTableNotEmpty(): Boolean

    @Query("UPDATE stored_image_data_table SET tags = tags || :newTags WHERE id = :entryId")
    fun updateTags(entryId: Int, newTags: String)

}