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

    /**
     * 同步导出用：某档案下所有「题目 ↔ 知识点」关联。
     *
     * 一次 JOIN 拿全，而不是逐题查询 —— 一个用了一年的档案可能上千道错题，
     * 逐题查会变成上千次数据库往返。
     */
    @Query(
        "SELECT link.* FROM question_knowledge link " +
            "INNER JOIN knowledge_point kp ON kp.id = link.knowledgePointId " +
            "WHERE kp.profileId = :profileId"
    )
    suspend fun getByProfileOnce(profileId: Long): List<QuestionKnowledgeEntity>

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