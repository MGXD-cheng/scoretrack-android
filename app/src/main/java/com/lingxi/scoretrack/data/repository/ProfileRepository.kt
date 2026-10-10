package com.lingxi.scoretrack.data.repository

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.dao.ProfileCleanupDao
import com.lingxi.scoretrack.data.local.dao.ProfileDao
import com.lingxi.scoretrack.data.local.dao.SubjectDao
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 档案仓库。
 * 新建档案时自动预置 9 门科目：语数英各 150，其余各 100（用户可编辑）。
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val subjectDao: SubjectDao,
    private val profileCleanupDao: ProfileCleanupDao,
    private val database: AppDatabase
) {

    fun observeAll(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    fun observeById(id: Long): Flow<ProfileEntity?> = profileDao.observeById(id)

    suspend fun getById(id: Long): ProfileEntity? = profileDao.getById(id)

    suspend fun getDefault(): ProfileEntity? = profileDao.getDefault()

    suspend fun getAllOnce(): List<ProfileEntity> = profileDao.getAllOnce()

    /** 创建档案并预置科目，返回新档案 id */
    suspend fun createWithDefaultSubjects(
        name: String,
        className: String? = null,
        gradeName: String? = null,
        remark: String? = null,
        isDefault: Boolean = false
    ): Long = database.withTransaction {
        if (isDefault) profileDao.clearDefault()
        val profileId = profileDao.insert(
            ProfileEntity(
                name = name,
                className = className,
                gradeName = gradeName,
                remark = remark,
                isDefault = isDefault
            )
        )
        subjectDao.insertAll(defaultSubjects(profileId))
        profileId
    }

    suspend fun update(profile: ProfileEntity) =
        profileDao.update(profile.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(profile: ProfileEntity) = profileDao.delete(profile)

    /**
     * 彻底删除档案及其全部关联数据（科目/考试/成绩/试卷/题目/错题/知识点/分析报告…）。
     *
     * 全程在一个事务里，按依赖顺序显式级联，不依赖 PRAGMA foreign_keys。
     * 如果删掉的是默认档案，自动把剩下的第一个顶上 —— 避免出现「没有默认档案」的状态。
     */
    suspend fun deleteCascade(profileId: Long) = database.withTransaction {
        profileCleanupDao.deleteQuestionKnowledge(profileId)
        profileCleanupDao.deleteMistakes(profileId)
        profileCleanupDao.deleteQuestions(profileId)
        profileCleanupDao.deletePapers(profileId)
        profileCleanupDao.deleteScores(profileId)
        profileCleanupDao.deleteAnalysisReports(profileId)
        profileCleanupDao.deleteImportTasks(profileId)
        profileCleanupDao.deleteKnowledgePoints(profileId)
        profileCleanupDao.deleteSubjects(profileId)
        profileCleanupDao.deleteExams(profileId)
        profileDao.deleteById(profileId)

        val remaining = profileDao.getAllOnce()
        if (remaining.isNotEmpty() && remaining.none { it.isDefault }) {
            profileDao.markDefault(remaining.first().id)
        }
    }

    suspend fun setDefault(id: Long) = database.withTransaction {
        profileDao.clearDefault()
        profileDao.markDefault(id)
    }

    companion object {

        /** 预置全科：语数英 150，其余 100 */
        fun defaultSubjects(profileId: Long): List<SubjectEntity> = listOf(
            SubjectEntity(profileId = profileId, name = "语文", fullScore = 150f, color = "#2A6DF4", sortOrder = 0),
            SubjectEntity(profileId = profileId, name = "数学", fullScore = 150f, color = "#22C55E", sortOrder = 1),
            SubjectEntity(profileId = profileId, name = "英语", fullScore = 150f, color = "#F59E0B", sortOrder = 2),
            SubjectEntity(profileId = profileId, name = "物理", fullScore = 100f, color = "#EF4444", sortOrder = 3),
            SubjectEntity(profileId = profileId, name = "化学", fullScore = 100f, color = "#8B5CF6", sortOrder = 4),
            SubjectEntity(profileId = profileId, name = "生物", fullScore = 100f, color = "#06B6D4", sortOrder = 5),
            SubjectEntity(profileId = profileId, name = "政治", fullScore = 100f, color = "#EC4899", sortOrder = 6),
            SubjectEntity(profileId = profileId, name = "历史", fullScore = 100f, color = "#84CC16", sortOrder = 7),
            SubjectEntity(profileId = profileId, name = "地理", fullScore = 100f, color = "#F97316", sortOrder = 8)
        )
    }
}