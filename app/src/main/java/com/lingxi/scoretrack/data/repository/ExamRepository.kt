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

    /**
     * 一次级联删除会带走多少条附属数据。
     *
     * 用户看到「删除考试」时，脑子里想的是「这场考试不记了」，而不是
     * 「我记的那十几道题也没了」。这些数字必须提前摊开给他看。
     */
    data class ExamCascadeCount(
        val papers: Int = 0,
        val questions: Int = 0
    ) {
        val hasAttachments: Boolean get() = papers > 0 || questions > 0
    }

    suspend fun countCascade(examId: Long): ExamCascadeCount = ExamCascadeCount(
        papers = examDao.countPapers(examId),
        questions = examDao.countQuestions(examId)
    )

    /**
     * 删除一场考试，连同它下面的试卷和题目。
     *
     * 顺序是从叶子往根删（题目 → 试卷 → 成绩 → 考试），
     * 这样即使外键约束某个时刻没生效，中途也不会留下「父没了子还在」的孤儿行。
     * 整个操作用事务包住：删一半失败就全部回滚，不会出现「成绩没了但考试还在」。
     */
    suspend fun deleteWithCascade(exam: ExamEntity) = database.withTransaction {
        examDao.deleteQuestionsOfExam(exam.id)
        examDao.deletePapersOfExam(exam.id)
        scoreDao.deleteByExam(exam.id)
        examDao.delete(exam)
    }

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