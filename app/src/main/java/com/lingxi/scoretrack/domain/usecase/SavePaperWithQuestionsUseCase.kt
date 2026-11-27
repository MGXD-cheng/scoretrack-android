package com.lingxi.scoretrack.domain.usecase

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.repository.PaperRepository
import com.lingxi.scoretrack.data.repository.QuestionRepository
import com.lingxi.scoretrack.domain.model.QuestionDraft
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保存一份试卷的小题分。
 *
 * 一个事务里做三件事：删题、写题、保存试卷。
 */
@Singleton
class SavePaperWithQuestionsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val paperRepository: PaperRepository,
    private val questionRepository: QuestionRepository,
) {

    suspend fun invoke(
        profileId: Long,
        examId: Long,
        subjectId: Long,
        title: String,
        drafts: List<QuestionDraft>
    ): Long = database.withTransaction {
        val totalFullScore = drafts.sumOf { it.fullScoreValue.toDouble() }.toFloat()

        // 1. 试卷：同一次考试的同一科目只有一份
        val existing = paperRepository.getByExamAndSubject(examId, subjectId)
        val paperId = if (existing == null) {
            paperRepository.insert(
                PaperEntity(
                    examId = examId,
                    subjectId = subjectId,
                    title = title,
                    totalScore = totalFullScore,
                    sourceType = "manual"
                )
            )
        } else {
            paperRepository.update(
                existing.copy(
                    title = title,
                    totalScore = totalFullScore,
                    sourceType = "manual"
                )
            )
            existing.id
        }

        // 2. 删掉用户移除的题
        val keptIds = drafts.mapNotNullTo(mutableSetOf()) { draft ->
            draft.id.takeIf { it > 0L }
        }
        questionRepository.getByPaper(paperId)
            .filter { it.id !in keptIds }
            .forEach { questionRepository.delete(it) }

        // 3. 逐题写入
        drafts.forEach { draft ->
            val entity = draft.toEntity(paperId)
            if (draft.id > 0L) {
                questionRepository.update(entity)
            } else {
                questionRepository.insert(entity)
            }
        }

        paperId
    }

    private fun QuestionDraft.toEntity(paperId: Long) = QuestionEntity(
        id = id,
        paperId = paperId,
        number = number.ifBlank { "-" },
        // 之前这里一直写死 null —— QuestionEntity.type 有字段、题目详情也在读，
        // 但从来没有值被写进去过。题型分类之所以是空的，根因就在这一行。
        type = type.takeIf { it.isNotBlank() },
        fullScore = fullScoreValue,
        score = scoreValue,
        difficulty = difficulty,
        content = content.ifBlank { null },
        answer = answer.ifBlank { null },
        userAnswer = userAnswer.ifBlank { null },
        isCorrect = !isWrong,
        wrongReason = wrongReason.ifBlank { null }
    )
}