package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisReportDao {

    @Query("SELECT * FROM analysis_report WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeByProfile(profileId: Long): Flow<List<AnalysisReportEntity>>

    @Query("SELECT * FROM analysis_report WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getByProfile(profileId: Long): List<AnalysisReportEntity>

    @Query("SELECT * FROM analysis_report WHERE id = :id")
    suspend fun getById(id: Long): AnalysisReportEntity?

    @Query("SELECT * FROM analysis_report WHERE profileId = :profileId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(profileId: Long): AnalysisReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: AnalysisReportEntity): Long

    @Delete
    suspend fun delete(report: AnalysisReportEntity)

    @Query("DELETE FROM analysis_report WHERE id = :id")
    suspend fun deleteById(id: Long)
}