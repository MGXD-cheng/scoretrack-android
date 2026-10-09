package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.ScoreDao
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoreRepository @Inject constructor(
    private val scoreDao: ScoreDao
) {

    fun observeByExam(examId: Long): Flow<List<ScoreEntity>> = scoreDao.observeByExam(examId)

    fun observeByProfile(profileId: Long): Flow<List<ScoreEntity>> =
        scoreDao.observeByProfile(profileId)

    fun observeByProfileAndSubject(profileId: Long, subjectId: Long): Flow<List<ScoreEntity>> =
        scoreDao.observeByProfileAndSubject(profileId, subjectId)

    suspend fun getByExam(examId: Long): List<ScoreEntity> = scoreDao.getByExam(examId)

    suspend fun getByExamAndSubject(examId: Long, subjectId: Long): ScoreEntity? =
        scoreDao.getByExamAndSubject(examId, subjectId)

    /** 新增或覆盖某科成绩（同一考试同一科目唯一） */
    suspend fun save(score: ScoreEntity): Long =
        scoreDao.insert(score.copy(updatedAt = System.currentTimeMillis()))

    suspend fun saveAll(scores: List<ScoreEntity>): List<Long> =
        scoreDao.insertAll(scores.map { it.copy(updatedAt = System.currentTimeMillis()) })

    suspend fun delete(score: ScoreEntity) = scoreDao.delete(score)

    suspend fun deleteByExam(examId: Long) = scoreDao.deleteByExam(examId)
}