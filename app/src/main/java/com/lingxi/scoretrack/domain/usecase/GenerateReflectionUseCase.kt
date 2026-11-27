package com.lingxi.scoretrack.domain.usecase

import com.lingxi.scoretrack.data.repository.AiRepository
import com.lingxi.scoretrack.data.repository.ExamGoalRepository
import com.lingxi.scoretrack.domain.ai.ReflectionPrompt
import com.lingxi.scoretrack.domain.session.ProfileSession
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 根据「这一场考试的成绩 + 用户自己的反思」生成一段复盘建议。
 *
 * 入参是**界面上的数据**而不是 examId，这样新建考试、还没保存时也能用 ——
 * 用户往往是刚录完分数、脑子里还有印象，这时候写反思最自然，
 * 让他先保存再看 AI 就太晚了。
 */
class GenerateReflectionUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val examGoalRepository: ExamGoalRepository,
    private val profileSession: ProfileSession
) {

    /**
     * @param entries 已填分数的科目。空列表没有分析价值，调用方应先拦掉
     * @param reflection 用户写的反思。允许为空 —— 会退化成「帮他找原因」
     */
    suspend operator fun invoke(
        examName: String,
        examType: String,
        entries: List<ReflectionEntry>,
        reflection: String
    ): Result<String> {
        if (entries.isEmpty()) {
            return Result.failure(IllegalStateException("先填几科成绩，再来分析吧"))
        }

        // 目标分是「锦上添花」：查不到就当作没设目标，绝不该因此失败
        val goals = runCatching {
            val profileId = profileSession.currentProfileId.first()
            examGoalRepository.observeByProfile(profileId).first()
        }.getOrDefault(emptyList())
        val goalBySubjectId = goals
            .filter { it.subjectId != null }
            .associate { it.subjectId!! to it.targetScore }

        val digest = ReflectionPrompt.buildDigest(
            examName = examName.ifBlank { "这次考试" },
            examType = examType,
            entries = entries.map {
                ReflectionPrompt.Entry(
                    subjectName = it.subjectName,
                    score = it.score,
                    fullScore = it.fullScore,
                    target = goalBySubjectId[it.subjectId]
                )
            },
            reflection = reflection
        )

        return aiRepository
            .ask(
                prompt = ReflectionPrompt.USER_PREFIX + digest,
                systemPrompt = ReflectionPrompt.SYSTEM
            )
            .map { it.trim() }
            .mapCatching { text ->
                require(text.isNotBlank()) { "模型返回了空内容，再试一次" }
                // 提示词里已经要求纯文本，但模型偶尔还是会裹一层围栏
                text.removePrefix("```").removeSuffix("```").trim()
            }
    }
}

/** 一科的成绩。subjectId 用来对目标分，subjectName 是给模型看的 */
data class ReflectionEntry(
    val subjectId: Long,
    val subjectName: String,
    val score: Float,
    val fullScore: Float
)