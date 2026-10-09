package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.ImportTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportTaskDao {

    @Query("SELECT * FROM import_task WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeByProfile(profileId: Long): Flow<List<ImportTaskEntity>>

    @Query("SELECT * FROM import_task WHERE id = :id")
    suspend fun getById(id: Long): ImportTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ImportTaskEntity): Long

    @Update
    suspend fun update(task: ImportTaskEntity)

    @Delete
    suspend fun delete(task: ImportTaskEntity)

    @Query("DELETE FROM import_task WHERE id = :id")
    suspend fun deleteById(id: Long)
}