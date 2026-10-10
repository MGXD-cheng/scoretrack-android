package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.ExamGoalDao
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 考试目标仓库。
 *
 * 总分目标用 subjectId = null 表示，所以每次写入前都要先查一次 ——
 * 直接 REPLACE 会因为自增主键而不断累积重复行。
 */
@Singleton
class ExamGoalRepository @Inject constructor(
    private val examGoalDao: ExamGoalDao
) {

    fun observeByProfile(profileId: Long): Flow<List<ExamGoalEntity>> =
        examGoalDao.observeByProfile(profileId)

    /** 总分目标 */
    suspend fun setTotalGoal(profileId: Long, targetScore: Float, targetRank: Int? = null) {
        write(profileId = profileId, subjectId = null, targetScore = targetScore, targetRank = targetRank)
    }

    /** 单科目标 */
    suspend fun setSubjectGoal(profileId: Long, subjectId: Long, targetScore: Float) {
        write(profileId = profileId, subjectId = subjectId, targetScore = targetScore, targetRank = null)
    }

    suspend fun clearGoal(profileId: Long, subjectId: Long?) =
        examGoalDao.deleteBySubject(profileId, subjectId)

    private suspend fun write(
        profileId: Long,
        subjectId: Long?,
        targetScore: Float,
        targetRank: Int?
    ) {
        val existing = examGoalDao.find(profileId, subjectId)
        examGoalDao.upsert(
            existing?.copy(
                targetScore = targetScore,
                targetRank = targetRank,
                updatedAt = System.currentTimeMillis()
            ) ?: ExamGoalEntity(
                profileId = profileId,
                subjectId = subjectId,
                targetScore = targetScore,
                targetRank = targetRank
            )
        )
    }
}