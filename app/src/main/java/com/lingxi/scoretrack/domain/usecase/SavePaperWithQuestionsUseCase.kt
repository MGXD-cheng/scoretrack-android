package com.lingxi.scoretrack.domain.usecase

import androidx.room.withTransaction
import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.PaperRepository
import com.lingxi.scoretrack.data.repository.QuestionRepository
import com.lingxi.scoretrack.domain.model.QuestionDraft
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保存一份试卷的小题分。
 *
 * 一个事务里做三件事：**增删改题目** + **同步错题本**。
 *
 * 错题本的同步是这里最容易被忽略、但最要紧的一步：
 * 用户把一道错题的分数改对之后，它必须**从错题本里消失** ——
 * 否则错题本会越用越脏，用户就不信它了。
 */
@Singleton
class SavePaperWithQuestionsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val paperRepository: PaperRepository,
    private val questionRepository: QuestionRepository,
    private val mistakeRepository: MistakeRepository
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

        // 2. 删掉用户移除的题（错题会随 FK CASCADE 一起清掉）
        val keptIds = drafts.mapNotNullTo(mutableSetOf()) { draft ->
            draft.id.takeIf { it > 0L }
        }
        questionRepository.getByPaper(paperId)
            .filter { it.id !in keptIds }
            .forEach { questionRepository.delete(it) }

        // 3. 逐题写入，并同步错题本
        drafts.forEach { draft ->
            val entity = draft.toEntity(paperId)
            val questionId = if (draft.id > 0L) {
                questionRepository.update(entity)
                draft.id
            } else {
                questionRepository.insert(entity)
            }

            if (draft.isWrong) {
                mistakeRepository.add(
                    profileId = profileId,
                    questionId = questionId,
                    note = draft.wrongReason.ifBlank { null }
                )
            } else {
                // 改对了就从错题本移除
                mistakeRepository.deleteByQuestion(questionId)
            }
        }

        paperId
    }

    private fun QuestionDraft.toEntity(paperId: Long) = QuestionEntity(
        id = id,
        paperId = paperId,
        number = number.ifBlank { "-" },
        type = null,
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