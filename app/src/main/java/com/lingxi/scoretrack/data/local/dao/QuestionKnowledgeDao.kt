package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.local.entity.QuestionKnowledgeEntity

@Dao
interface QuestionKnowledgeDao {

    /** 某个知识点下挂着的题目 */
    @Query(
        """
        SELECT question.* FROM question
        INNER JOIN question_knowledge link ON link.questionId = question.id
        WHERE link.knowledgePointId = :knowledgePointId
        ORDER BY question.id ASC
        """
    )
    suspend fun getQuestionsOfPoint(knowledgePointId: Long): List<QuestionEntity>

    @Query("SELECT * FROM question_knowledge WHERE questionId = :questionId")
    suspend fun getByQuestion(questionId: Long): List<QuestionKnowledgeEntity>

    @Query("SELECT * FROM question_knowledge WHERE knowledgePointId = :knowledgePointId")
    suspend fun getByKnowledgePoint(knowledgePointId: Long): List<QuestionKnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: QuestionKnowledgeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<QuestionKnowledgeEntity>)

    @Delete
    suspend fun delete(link: QuestionKnowledgeEntity)

    @Query("DELETE FROM question_knowledge WHERE questionId = :questionId")
    suspend fun deleteByQuestion(questionId: Long)

    @Query("DELETE FROM question_knowledge WHERE knowledgePointId = :knowledgePointId")
    suspend fun deleteByKnowledgePoint(knowledgePointId: Long)
}