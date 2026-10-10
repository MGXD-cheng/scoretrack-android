package com.lingxi.scoretrack.domain.usecase

import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.repository.AiRepository
import com.lingxi.scoretrack.data.repository.AnalysisReportRepository
import com.lingxi.scoretrack.data.repository.ExamGoalRepository
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.ProfileRepository
import com.lingxi.scoretrack.data.repository.ScoreRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.domain.ai.AnalysisPrompt
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生成一份综合学习诊断报告。
 *
 * 流程：收集数据 → 组装摘要 → 调文本模型 → 落库。
 *
 * 为什么用文本模型而不是视觉：这一步没有图片，纯文本推理，
 * deepseek-chat 这类模型又快又便宜，没必要走视觉通道。
 */
@Singleton
class GenerateAnalysisUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val examRepository: ExamRepository,
    private val examGoalRepository: ExamGoalRepository,
    private val scoreRepository: ScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val mistakeRepository: MistakeRepository,
    private val analysisReportRepository: AnalysisReportRepository,
    private val aiRepository: AiRepository
) {

    suspend fun invoke(profileId: Long): Result<Long> {
        val profile = profileRepository.getById(profileId)
            ?: return Result.failure(IllegalStateException("档案不存在"))

        val exams = examRepository.getByProfile(profileId)
        if (exams.isEmpty()) {
            return Result.failure(
                IllegalStateException("还没有考试记录，先录入一次成绩再来分析吧")
            )
        }

        val subjects = subjectRepository.getByProfile(profileId)
        val scores = scoreRepository.observeByProfile(profileId).first()
        val mistakes = mistakeRepository.observeDetailByProfile(profileId).first()
        val goals = examGoalRepository.observeByProfile(profileId).first()

        val digest = AnalysisPrompt.buildDataDigest(
            profileName = profile.name,
            exams = exams,
            scores = scores,
            subjects = subjects,
            mistakes = mistakes,
            goals = goals
        )

        return aiRepository
            .ask(
                prompt = AnalysisPrompt.USER_PREFIX + digest,
                systemPrompt = AnalysisPrompt.SYSTEM
            )
            .mapCatching { markdown ->
                val content = stripCodeFence(markdown)
                require(content.isNotBlank()) { "模型返回了空报告，请重试" }

                analysisReportRepository.insert(
                    AnalysisReportEntity(
                        profileId = profileId,
                        examId = null,
                        type = "综合",
                        content = content
                    )
                )
            }
    }

    /** 有些模型还是会把 Markdown 包在代码块里 */
    private fun stripCodeFence(text: String): String =
        text.trim()
            .removePrefix("```markdown")
            .removePrefix("```md")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
}