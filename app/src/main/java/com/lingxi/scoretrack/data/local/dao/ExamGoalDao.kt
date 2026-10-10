package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamGoalDao {

    @Query("SELECT * FROM exam_goal WHERE profileId = :profileId")
    fun observeByProfile(profileId: Long): Flow<List<ExamGoalEntity>>

    /**
     * 查某一个目标。
     *
     * 注意必须用 `IS` 而不是 `=` —— `subjectId = NULL` 在 SQL 里恒为 NULL，
     * 永远查不到总分目标那一行。
     */
    @Query("SELECT * FROM exam_goal WHERE profileId = :profileId AND subjectId IS :subjectId LIMIT 1")
    suspend fun find(profileId: Long, subjectId: Long?): ExamGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: ExamGoalEntity): Long

    @Delete
    suspend fun delete(goal: ExamGoalEntity)

    @Query("DELETE FROM exam_goal WHERE profileId = :profileId AND subjectId IS :subjectId")
    suspend fun deleteBySubject(profileId: Long, subjectId: Long?)
}