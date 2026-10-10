package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.MistakeEntity
import com.lingxi.scoretrack.data.local.model.MistakeDetailRow
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeDao {

    @Query("SELECT * FROM mistake WHERE profileId = :profileId ORDER BY lastReviewAt DESC, id DESC")
    fun observeByProfile(profileId: Long): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistake WHERE profileId = :profileId ORDER BY lastReviewAt DESC, id DESC")
    suspend fun getByProfile(profileId: Long): List<MistakeEntity>

    /** 待复习错题数（未掌握的都算） */
    @Query("SELECT COUNT(*) FROM mistake WHERE profileId = :profileId AND reviewStatus != '已掌握'")
    fun observePendingCount(profileId: Long): Flow<Int>

    @Query("SELECT * FROM mistake WHERE id = :id")
    suspend fun getById(id: Long): MistakeEntity?

    /**
     * 错题详情（带考试/科目/题目上下文的 join 视图）。
     * 注意：用 INNER JOIN，所以底层题目被删掉的错题不会出现在列表里。
     */
    @Query(
        """
        SELECT mistake.id AS mistakeId,
               mistake.note AS note,
               mistake.reviewStatus AS reviewStatus,
               mistake.reviewCount AS reviewCount,
               mistake.lastReviewAt AS lastReviewAt,
               question.id AS questionId,
               question.number AS number,
               question.type AS type,
               question.content AS content,
               question.answer AS answer,
               question.userAnswer AS userAnswer,
               question.wrongReason AS wrongReason,
               question.score AS score,
               question.fullScore AS fullScore,
               question.difficulty AS difficulty,
               paper.subjectId AS subjectId,
               paper.title AS paperTitle,
               exam.name AS examName
        FROM mistake
        INNER JOIN question ON mistake.questionId = question.id
        INNER JOIN paper ON question.paperId = paper.id
        INNER JOIN exam ON paper.examId = exam.id
        WHERE mistake.profileId = :profileId
        ORDER BY mistake.lastReviewAt DESC, mistake.id DESC
        """
    )
    fun observeDetailByProfile(profileId: Long): Flow<List<MistakeDetailRow>>

    /**
     * 与 [observeDetailByProfile] 相同的 join，一次取完。
     * 给「AI 归纳知识点」这类一次性分析用 —— 分析不需要响应式订阅。
     */
    @Query(
        """
        SELECT mistake.id AS mistakeId,
               mistake.note AS note,
               mistake.reviewStatus AS reviewStatus,
               mistake.reviewCount AS reviewCount,
               mistake.lastReviewAt AS lastReviewAt,
               question.id AS questionId,
               question.number AS number,
               question.type AS type,
               question.content AS content,
               question.answer AS answer,
               question.userAnswer AS userAnswer,
               question.wrongReason AS wrongReason,
               question.score AS score,
               question.fullScore AS fullScore,
               question.difficulty AS difficulty,
               paper.subjectId AS subjectId,
               paper.title AS paperTitle,
               exam.name AS examName
        FROM mistake
        INNER JOIN question ON mistake.questionId = question.id
        INNER JOIN paper ON question.paperId = paper.id
        INNER JOIN exam ON paper.examId = exam.id
        WHERE mistake.profileId = :profileId
        ORDER BY mistake.lastReviewAt DESC, mistake.id DESC
        """
    )
    suspend fun getDetailByProfile(profileId: Long): List<MistakeDetailRow>

    @Query(
        """
        SELECT mistake.id AS mistakeId,
               mistake.note AS note,
               mistake.reviewStatus AS reviewStatus,
               mistake.reviewCount AS reviewCount,
               mistake.lastReviewAt AS lastReviewAt,
               question.id AS questionId,
               question.number AS number,
               question.type AS type,
               question.content AS content,
               question.answer AS answer,
               question.userAnswer AS userAnswer,
               question.wrongReason AS wrongReason,
               question.score AS score,
               question.fullScore AS fullScore,
               question.difficulty AS difficulty,
               paper.subjectId AS subjectId,
               paper.title AS paperTitle,
               exam.name AS examName
        FROM mistake
        INNER JOIN question ON mistake.questionId = question.id
        INNER JOIN paper ON question.paperId = paper.id
        INNER JOIN exam ON paper.examId = exam.id
        WHERE mistake.id = :mistakeId
        """
    )
    fun observeDetailById(mistakeId: Long): Flow<MistakeDetailRow?>

    @Query("SELECT * FROM mistake WHERE profileId = :profileId AND questionId = :questionId LIMIT 1")
    suspend fun getByProfileAndQuestion(profileId: Long, questionId: Long): MistakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mistake: MistakeEntity): Long

    @Update
    suspend fun update(mistake: MistakeEntity)

    @Delete
    suspend fun delete(mistake: MistakeEntity)

    @Query("DELETE FROM mistake WHERE questionId = :questionId")
    suspend fun deleteByQuestion(questionId: Long)
}