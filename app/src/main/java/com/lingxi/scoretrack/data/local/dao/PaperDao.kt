package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperDao {

    @Query("SELECT * FROM paper WHERE examId = :examId")
    fun observeByExam(examId: Long): Flow<List<PaperEntity>>

    @Query("SELECT * FROM paper WHERE examId = :examId AND subjectId = :subjectId LIMIT 1")
    suspend fun getByExamAndSubject(examId: Long, subjectId: Long): PaperEntity?

    @Query("SELECT * FROM paper WHERE id = :id")
    suspend fun getById(id: Long): PaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(paper: PaperEntity): Long

    @Update
    suspend fun update(paper: PaperEntity)

    @Delete
    suspend fun delete(paper: PaperEntity)

    @Query("DELETE FROM paper WHERE id = :id")
    suspend fun deleteById(id: Long)
}