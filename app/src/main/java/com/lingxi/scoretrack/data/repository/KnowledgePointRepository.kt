package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.KnowledgePointDao
import com.lingxi.scoretrack.data.local.dao.QuestionKnowledgeDao
import com.lingxi.scoretrack.data.local.entity.KnowledgePointEntity
import com.lingxi.scoretrack.data.local.entity.QuestionKnowledgeEntity
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
}