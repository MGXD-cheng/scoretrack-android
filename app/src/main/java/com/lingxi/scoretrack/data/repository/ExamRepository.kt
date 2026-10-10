package com.lingxi.scoretrack.data.repository

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.dao.ExamDao
import com.lingxi.scoretrack.data.local.dao.ScoreDao
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamRepository @Inject constructor(
    private val examDao: ExamDao,
    private val scoreDao: ScoreDao,
    private val database: AppDatabase
) {

    fun observeByProfile(profileId: Long): Flow<List<ExamEntity>> =
        examDao.observeByProfile(profileId)

    fun observeById(id: Long): Flow<ExamEntity?> = examDao.observeById(id)

    fun observeLatest(profileId: Long): Flow<ExamEntity?> = examDao.observeLatest(profileId)

    suspend fun getByProfile(profileId: Long): List<ExamEntity> = examDao.getByProfile(profileId)

    suspend fun getById(id: Long): ExamEntity? = examDao.getById(id)

    suspend fun getLatest(profileId: Long): ExamEntity? = examDao.getLatest(profileId)

    suspend fun insert(exam: ExamEntity): Long = examDao.insert(exam)

    suspend fun update(exam: ExamEntity) =
        examDao.update(exam.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(exam: ExamEntity) = examDao.delete(exam)

    /**
     * 原子写入：考试 + 其各科成绩。
     * 放在同一事务里，避免「考试建好了但成绩只写了一半」的脏状态。
     */
    suspend fun saveExamWithScores(
        exam: ExamEntity,
        scores: List<ScoreEntity>
    ): Long = database.withTransaction {
        val examId = if (exam.id == 0L) {
            examDao.insert(exam)
        } else {
            examDao.update(exam.copy(updatedAt = System.currentTimeMillis()))
            exam.id
        }

        // 先清空该考试原有成绩，再整体写入（简单可靠，避免残留脏数据）
        scoreDao.deleteByExam(examId)
        if (scores.isNotEmpty()) {
            val now = System.currentTimeMillis()
            scoreDao.insertAll(scores.map { it.copy(examId = examId, updatedAt = now) })
        }
        examId
    }
}