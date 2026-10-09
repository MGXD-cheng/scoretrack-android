package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC")
    fun observeByProfile(profileId: Long): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC")
    suspend fun getByProfile(profileId: Long): List<ExamEntity>

    @Query("SELECT * FROM exam WHERE id = :id")
    suspend fun getById(id: Long): ExamEntity?

    @Query("SELECT * FROM exam WHERE id = :id")
    fun observeById(id: Long): Flow<ExamEntity?>

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(profileId: Long): ExamEntity?

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC LIMIT 1")
    fun observeLatest(profileId: Long): Flow<ExamEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exam: ExamEntity): Long

    @Update
    suspend fun update(exam: ExamEntity)

    @Delete
    suspend fun delete(exam: ExamEntity)
}