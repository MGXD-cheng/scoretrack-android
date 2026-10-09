package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Query("SELECT * FROM subject WHERE profileId = :profileId ORDER BY sort_order ASC, id ASC")
    fun observeByProfile(profileId: Long): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subject WHERE profileId = :profileId ORDER BY sort_order ASC, id ASC")
    suspend fun getByProfile(profileId: Long): List<SubjectEntity>

    @Query("SELECT * FROM subject WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subject WHERE profileId = :profileId AND name = :name LIMIT 1")
    suspend fun getByName(profileId: Long, name: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<SubjectEntity>): List<Long>

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}