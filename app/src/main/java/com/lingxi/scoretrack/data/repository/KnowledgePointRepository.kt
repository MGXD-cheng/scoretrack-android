package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.KnowledgePointDao
import com.lingxi.scoretrack.data.local.dao.QuestionKnowledgeDao
import com.lingxi.scoretrack.data.local.entity.KnowledgePointEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.local.entity.QuestionKnowledgeEntity
import com.lingxi.scoretrack.data.local.model.KnowledgePointRow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 知识点仓库，同时负责「题目 ↔ 知识点」的关联维护。
 */
@Singleton
class KnowledgePointRepository @Inject constructor(
    private val knowledgePointDao: KnowledgePointDao,
    private val linkDao: QuestionKnowledgeDao
) {

    fun observeByProfile(profileId: Long): Flow<List<KnowledgePointEntity>> =
        knowledgePointDao.observeByProfile(profileId)

    fun observeBySubject(profileId: Long, subjectId: Long): Flow<List<KnowledgePointEntity>> =
        knowledgePointDao.observeBySubject(profileId, subjectId)

    suspend fun getBySubject(profileId: Long, subjectId: Long): List<KnowledgePointEntity> =
        knowledgePointDao.getBySubject(profileId, subjectId)

    suspend fun getByProfile(profileId: Long): List<KnowledgePointEntity> =
        knowledgePointDao.getByProfile(profileId)

    suspend fun getById(id: Long): KnowledgePointEntity? = knowledgePointDao.getById(id)

    suspend fun insert(point: KnowledgePointEntity): Long = knowledgePointDao.insert(point)

    suspend fun insertAll(points: List<KnowledgePointEntity>): List<Long> =
        knowledgePointDao.insertAll(points)

    suspend fun update(point: KnowledgePointEntity) =
        knowledgePointDao.update(point.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(point: KnowledgePointEntity) = knowledgePointDao.delete(point)

    // ---------- 题目 ↔ 知识点 关联 ----------

    suspend fun link(questionId: Long, knowledgePointId: Long) =
        linkDao.insert(QuestionKnowledgeEntity(questionId, knowledgePointId))

    suspend fun linkAll(links: List<QuestionKnowledgeEntity>) = linkDao.insertAll(links)

    suspend fun unlink(questionId: Long, knowledgePointId: Long) =
        linkDao.delete(QuestionKnowledgeEntity(questionId, knowledgePointId))

    suspend fun linksOfQuestion(questionId: Long): List<QuestionKnowledgeEntity> =
        linkDao.getByQuestion(questionId)

    suspend fun linksOfPoint(knowledgePointId: Long): List<QuestionKnowledgeEntity> =
        linkDao.getByKnowledgePoint(knowledgePointId)

    /** 知识点总览（带挂载题数） */
    fun observeOverview(profileId: Long): Flow<List<KnowledgePointRow>> =
        knowledgePointDao.observeOverview(profileId)

    /** 某个知识点下的全部题目 */
    suspend fun questionsOfPoint(knowledgePointId: Long): List<QuestionEntity> =
        linkDao.getQuestionsOfPoint(knowledgePointId)

    suspend fun findByName(profileId: Long, subjectId: Long, name: String): KnowledgePointEntity? =
        knowledgePointDao.findByName(profileId, subjectId, name)

    /**
     * 手动归类：把一道题挂到某个知识点上。
     *
     * 知识点不存在时**顺手建出来** —— 用户想归类却要先跑去"新建知识点"，
     * 这种流程没人受得了。手工建的知识点 source 标为 manual，
     * 以后 AI 再归纳时不会把它当成自己生成的产物。
     */
    suspend fun ensurePointAndLink(
        profileId: Long,
        subjectId: Long,
        questionId: Long,
        name: String
    ): Long {
        val trimmed = name.trim()
        val existing = knowledgePointDao.findByName(profileId, subjectId, trimmed)
        val pointId = existing?.id ?: knowledgePointDao.insert(
            KnowledgePointEntity(
                profileId = profileId,
                subjectId = subjectId,
                name = trimmed,
                source = "manual"
            )
        )
        linkDao.insert(QuestionKnowledgeEntity(questionId, pointId))
        return pointId
    }
}