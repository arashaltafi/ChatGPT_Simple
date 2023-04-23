package com.arash.altafi.chatgptsimple.data.local

import androidx.room.*

@Dao
interface MessengerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDialog(dialogEntity: DialogEntity)

    @Query("SELECT * FROM tbl_dialog")
    fun getAllDialog(): List<DialogEntity>

    @Query("SELECT * FROM tbl_dialog WHERE id = :id")
    fun getDialogById(id: Long): DialogEntity

    @Query("DELETE FROM tbl_dialog WHERE id = :id")
    fun deleteDialogById(id: Long)

    @Query("SELECT id FROM tbl_dialog ORDER BY id DESC LIMIT 1")
    fun getLastDialogId(): Long

    @Delete
    fun deleteDialog(dialogEntity: DialogEntity)

    @Update
    fun updateDialog(dialogEntity: DialogEntity)

}