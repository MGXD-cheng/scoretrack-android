package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.MistakeDao
import com.lingxi.scoretrack.data.local.entity.MistakeEntity
import com.lingxi.scoretrack.data.local.model.MistakeDetailRow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 错题本仓库。复习状态：未复习 / 复习中 / 已掌握。
 */
@Singleton
class MistakeRepository @Inject constructor(
    private val mistakeDao: MistakeDao
) {

    fun observeByProfile(profileId: Long): Flow<List<MistakeEntity>> =
        mistakeDao.observeByProfile(profileId)

    /** 带上下文（考试/科目/题目）的错题列表 */
    fun observeDetailByProfile(profileId: Long): Flow<List<MistakeDetailRow>> =
        mistakeDao.observeDetailByProfile(profileId)
    fun observeDetailById(mistakeId: Long): Flow<MistakeDetailRow?> =
        mistakeDao.observeDetailById(mistakeId)

    /** 一次性取全部错题详情，给 AI 归纳知识点用 */
    suspend fun getDetailByProfile(profileId: Long): List<MistakeDetailRow> =
        mistakeDao.getDetailByProfile(profileId)

    fun observePendingCount(profileId: Long): Flow<Int> =
        mistakeDao.observePendingCount(profileId)

    suspend fun getByProfile(profileId: Long): List<MistakeEntity> =
        mistakeDao.getByProfile(profileId)

    suspend fun getById(id: Long): MistakeEntity? = mistakeDao.getById(id)

    /** 加入错题本（已存在则更新备注，不重复插入） */
    suspend fun add(profileId: Long, questionId: Long, note: String? = null): Long {
        val existing = mistakeDao.getByProfileAndQuestion(profileId, questionId)
        return if (existing != null) {
            mistakeDao.insert(existing.copy(note = note ?: existing.note))
        } else {
            mistakeDao.insert(
                MistakeEntity(profileId = profileId, questionId = questionId, note = note)
            )
        }
    }

    suspend fun update(mistake: MistakeEntity) = mistakeDao.update(mistake)

    /** 更新复习状态并累加复习次数 */
    suspend fun setReviewStatus(mistake: MistakeEntity, status: String) =
        mistakeDao.update(
            mistake.copy(
                reviewStatus = status,
                reviewCount = mistake.reviewCount + 1,
                lastReviewAt = System.currentTimeMillis()
            )
        )

    suspend fun delete(mistake: MistakeEntity) = mistakeDao.delete(mistake)

    suspend fun deleteByQuestion(questionId: Long) = mistakeDao.deleteByQuestion(questionId)
}