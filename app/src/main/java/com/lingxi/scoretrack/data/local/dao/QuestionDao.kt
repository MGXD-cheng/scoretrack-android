package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Query("SELECT * FROM question WHERE paperId = :paperId ORDER BY id ASC")
    fun observeByPaper(paperId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM question WHERE paperId = :paperId ORDER BY id ASC")
    suspend fun getByPaper(paperId: Long): List<QuestionEntity>

    @Query("SELECT * FROM question WHERE id = :id")
    suspend fun getById(id: Long): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Update
    suspend fun update(question: QuestionEntity)

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("DELETE FROM question WHERE paperId = :paperId")
    suspend fun deleteByPaper(paperId: Long)
}