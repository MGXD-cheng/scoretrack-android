package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {

    @Query("SELECT * FROM score WHERE examId = :examId")
    fun observeByExam(examId: Long): Flow<List<ScoreEntity>>

    @Query("SELECT * FROM score WHERE examId = :examId")
    suspend fun getByExam(examId: Long): List<ScoreEntity>

    @Query("SELECT * FROM score WHERE examId = :examId AND subjectId = :subjectId LIMIT 1")
    suspend fun getByExamAndSubject(examId: Long, subjectId: Long): ScoreEntity?

    @Query("SELECT * FROM score WHERE id = :id")
    suspend fun getById(id: Long): ScoreEntity?

    /** 某档案下全部成绩（用于趋势 / 雷达图） */
    @Query(
        "SELECT score.* FROM score " +
            "INNER JOIN exam ON score.examId = exam.id " +
            "WHERE exam.profileId = :profileId " +
            "ORDER BY exam.date ASC"
    )
    fun observeByProfile(profileId: Long): Flow<List<ScoreEntity>>

    /** 某档案某科目的成绩序列（按考试时间升序，用于单科折线图） */
    @Query(
        "SELECT score.* FROM score " +
            "INNER JOIN exam ON score.examId = exam.id " +
            "WHERE exam.profileId = :profileId AND score.subjectId = :subjectId " +
            "ORDER BY exam.date ASC"
    )
    fun observeByProfileAndSubject(profileId: Long, subjectId: Long): Flow<List<ScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: ScoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scores: List<ScoreEntity>): List<Long>

    @Update
    suspend fun update(score: ScoreEntity)

    @Delete
    suspend fun delete(score: ScoreEntity)

    @Query("DELETE FROM score WHERE examId = :examId")
    suspend fun deleteByExam(examId: Long)
}