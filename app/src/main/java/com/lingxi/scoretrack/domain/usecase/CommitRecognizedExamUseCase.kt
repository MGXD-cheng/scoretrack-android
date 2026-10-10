package com.lingxi.scoretrack.domain.usecase

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ImportTaskEntity
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.ImportTaskRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.PaperRepository
import com.lingxi.scoretrack.data.repository.QuestionRepository
import com.lingxi.scoretrack.data.repository.ScoreRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.domain.model.RecognizeResult
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * 把 AI 识别结果写入数据库。
 *
 * **整个写入在一个事务里**：考试 → 科目 → 试卷 → 题目 → 成绩 → 错题。
 * 任一步失败全部回滚 —— 绝不能出现「考试建好了、但错题只写进去一半」的脏状态。
 *
 * 科目是复用优先：识别出「数学」就挂到已有科目上，识别出「技术」这种
 * 预置里没有的，就现建一个，不让用户为了导入先去手动加科目。
 */
@Singleton
class CommitRecognizedExamUseCase @Inject constructor(
    private val database: AppDatabase,
    private val examRepository: ExamRepository,
    private val subjectRepository: SubjectRepository,
    private val paperRepository: PaperRepository,
    private val questionRepository: QuestionRepository,
    private val scoreRepository: ScoreRepository,
    private val mistakeRepository: MistakeRepository,
    private val importTaskRepository: ImportTaskRepository
) {

    suspend fun invoke(
        profileId: Long,
        result: RecognizeResult,
        sourceType: String = "photo",
        sourceFileUrl: String? = null,
        /** 原始文件类型：image / pdf / csv / xlsx，记录在 import_task 里便于追溯 */
        fileType: String = "image"
    ): Long = database.withTransaction {
        val recognized = result.exam
        val now = System.currentTimeMillis()

        // 1. 考试主体
        val examId = examRepository.insert(
            ExamEntity(
                profileId = profileId,
                name = recognized.examName,
                type = "常规考试",
                date = parseDate(recognized.examDate) ?: now,
                remark = "由成绩单识别导入"
            )
        )

        // 2. 逐科落库
        recognized.subjects.forEach { subject ->
            val subjectEntity = resolveSubject(profileId, subject.subjectName, subject.fullScore)

            // 各科总分
            scoreRepository.save(
                ScoreEntity(
                    examId = examId,
                    subjectId = subjectEntity.id,
                    score = subject.score,
                    fullScore = subject.fullScore
                )
            )

            // 没有逐题信息就到此为止（很多成绩单只给总分）
            if (subject.questions.isEmpty()) return@forEach

            val paperId = paperRepository.insert(
                PaperEntity(
                    examId = examId,
                    subjectId = subjectEntity.id,
                    title = "${subject.subjectName}试卷",
                    totalScore = subject.fullScore,
                    sourceType = sourceType,
                    sourceFileUrl = sourceFileUrl
                )
            )

            subject.questions.forEach { question ->
                val questionId = questionRepository.insert(
                    QuestionEntity(
                        paperId = paperId,
                        number = question.number,
                        type = null,
                        fullScore = question.fullScore ?: 0f,
                        score = question.score ?: 0f,
                        difficulty = question.difficulty,
                        content = question.content,
                        answer = question.correctAnswer,
                        userAnswer = question.userAnswer,
                        isCorrect = false,
                        wrongReason = question.wrongReason
                    )
                )
                // 识别出来的错题直接进错题本，用户不用再手动加一遍
                mistakeRepository.add(
                    profileId = profileId,
                    questionId = questionId,
                    note = question.wrongReason
                )
            }
        }

        // 3. 留一条导入记录，保留模型原始返回便于排查
        importTaskRepository.insert(
            ImportTaskEntity(
                profileId = profileId,
                fileUrl = sourceFileUrl,
                fileType = fileType,
                importType = "exam_sheet",
                status = "已完成",
                aiRawResult = result.rawResponse,
                confirmedResult = recognized.examName
            )
        )

        examId
    }

    /** 科目复用优先，没有就现建一个（排在预置科目后面） */
    private suspend fun resolveSubject(
        profileId: Long,
        subjectName: String,
        fullScore: Float
    ): SubjectEntity {
        subjectRepository.getByName(profileId, subjectName)?.let { return it }
        val newId = subjectRepository.insert(
            SubjectEntity(
                profileId = profileId,
                name = subjectName,
                fullScore = fullScore,
                color = pickColor(subjectName),
                sortOrder = 100
            )
        )
        return subjectRepository.getById(newId)
            ?: SubjectEntity(
                id = newId,
                profileId = profileId,
                name = subjectName,
                fullScore = fullScore,
                color = pickColor(subjectName),
                sortOrder = 100
            )
    }

    private fun parseDate(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(text)?.time
        }.getOrNull()
    }

    private fun pickColor(subjectName: String): String =
        PALETTE[subjectName.hashCode().absoluteValue % PALETTE.size]

    private companion object {
        val PALETTE = listOf(
            "#2A6DF4", "#22C55E", "#F59E0B", "#EF4444", "#8B5CF6",
            "#06B6D4", "#EC4899", "#84CC16", "#F97316"
        )
    }
}