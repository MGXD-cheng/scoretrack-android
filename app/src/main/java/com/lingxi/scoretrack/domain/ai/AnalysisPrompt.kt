package com.lingxi.scoretrack.domain.ai

import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.local.model.MistakeDetailRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 分析报告的提示词与数据摘要构造。
 *
 * 关键点：**给模型的不是原始数据 dump，而是已经算好的统计**。
 * 模型不擅长做算术（容易一本正经地算错平均值），
 * 这一步把能算的都算好，只让它做「解读」这件它真正擅长的事。
 */
object AnalysisPrompt {

    val SYSTEM: String = """
        你是一位耐心、专业的学习规划老师。学生会提供自己的历次考试成绩、错题统计，
如果学生设过考试目标，也会一并给出。请你据此写一份学习分析。

        格式要求（严格遵守）：
        - 使用中文，Markdown 格式
        - 用 `##` 作为一级小节标题，依次为：总体情况、目标差距、学科分析、需要关注、下一步建议
        - 可以使用 `-` 项目符号和 `**加粗**` 强调重点
        - 不要使用表格（终端渲染效果差）
        - 全文控制在 400–700 字

        内容要求：
        - **基于给出的数据说话**，指出具体哪科在进步、哪科在退步、哪科是短板
        - **如果数据里有考试目标，「目标差距」一节必须写清楚**：
          最近一次离总分目标还差多少分、哪些科目拖了后腿、哪些科目已经达标，
          并判断照当前趋势下次考试能否达到目标
        - 如果学生没有设定目标，就在「目标差距」一节里建议他先定一个可衡量的目标
        - 建议要具体可执行（例如"每天固定 20 分钟整理一道导数错题"），
          不要写"要努力学习""多做题"这类空话
        - 错题数据要充分利用：如果某科错题积压多，要点出来
        - 语气鼓励，但**不要空洞地夸奖**，也不要打击
        - 如果数据太少（只有 1 次考试），就如实说明样本有限，给出初步观察即可

        只输出 Markdown 正文，不要任何额外解释、不要代码块围栏。
    """.trimIndent()

    const val USER_PREFIX = "以下是我的成绩数据，请生成分析报告：\n\n"

    /** 摘要里最多带几次考试，避免 prompt 过长 */
    private const val MAX_EXAMS = 10

    /** 最多带几道错题作为例子 */
    private const val MAX_MISTAKES = 15

    /**
     * 把数据库里的原始记录，组装成一份**已经算好统计量**的文本摘要。
     */
    fun buildDataDigest(
        profileName: String,
        exams: List<ExamEntity>,
        scores: List<ScoreEntity>,
        subjects: List<SubjectEntity>,
        /**
         * 错题列表。
         * 直接复用错题本的 MistakeDetailRow —— 它已经 join 好了
         * mistake → question → paper → exam，题干、科目、状态一个不缺，
         * 这里没必要再写一遍同样的 SQL。
         */
        mistakes: List<MistakeDetailRow>,
        /** 考试目标。可能为空 —— 用户还没设过 */
        goals: List<ExamGoalEntity> = emptyList()
    ): String {
        val subjectById = subjects.associateBy { it.id }
        val scoresByExam = scores.groupBy { it.examId }
        val sortedExams = exams.sortedByDescending { it.date }

        return buildString {
            appendLine("学生档案：$profileName")
            appendLine("考试次数：${exams.size}")

            // ---------- 1. 历次考试 ----------
            if (sortedExams.isEmpty()) {
                appendLine()
                appendLine("（暂无考试记录）")
                return@buildString
            }

            appendLine()
            appendLine("【历次考试】（由近及远，最多 ${MAX_EXAMS} 次）")
            sortedExams.take(MAX_EXAMS).forEachIndexed { index, exam ->
                appendLine("${index + 1}. ${exam.name}（${formatDate(exam.date)}）")
                val examScores = scoresByExam[exam.id].orEmpty()
                if (examScores.isEmpty()) {
                    appendLine("   （该次考试未录入各科成绩）")
                } else {
                    val line = examScores.joinToString("；") { score ->
                        val name = subjectById[score.subjectId]?.name ?: "未知科目"
                        "$name ${trim(score.score)}/${trim(score.fullScore)}"
                    }
                    appendLine("   $line")
                }
            }

            // ---------- 2. 分科统计 ----------
            appendLine()
            appendLine("【分科统计】")
            subjects.forEach { subject ->
                val series = sortedExams.mapNotNull { exam ->
                    scoresByExam[exam.id]?.firstOrNull { it.subjectId == subject.id }
                }
                if (series.isEmpty()) return@forEach

                // series 已按时间倒序（最近的在最前）
                val rates = series.mapNotNull { it.rateOrNull() }
                val avgRate = rates.average()
                val latestRate = rates.firstOrNull()
                val bestRate = rates.maxOrNull()
                val trend = describeTrend(rates)

                appendLine(
                    "- ${subject.name}：共 ${series.size} 次，" +
                        "平均得分率 ${percent(avgRate)}，" +
                        "最好 ${percent(bestRate)}，" +
                        "最近一次 ${percent(latestRate)}，" +
                        "近期走势：$trend"
                )
            }

            // ---------- 3. 考试目标与差距 ----------
            // 算术全在这里做完，模型只负责解读 —— 它算数真的不靠谱
            appendLine()
            appendLine("【考试目标】")
            if (goals.isEmpty()) {
                appendLine("- 学生尚未设定考试目标")
            } else {
                val totalGoal = goals.firstOrNull { it.subjectId == null }
                val subjectGoals = goals.filter { it.subjectId != null }
                val latestExam = sortedExams.firstOrNull()
                val latestScores = latestExam?.let { scoresByExam[it.id] }.orEmpty()

                totalGoal?.let { goal ->
                    appendLine(
                        "- 总分目标：${trim(goal.targetScore)} 分" +
                            (goal.targetRank?.let { "（名次目标：前 $it）" } ?: "")
                    )
                    if (latestScores.isNotEmpty()) {
                        val latestTotal = latestScores.sumOf { it.score.toDouble() }.toFloat()
                        val fullTotal = latestScores.sumOf { it.fullScore.toDouble() }.toFloat()
                        val gap = goal.targetScore - latestTotal
                        val rate = if (goal.targetScore > 0f) {
                            latestTotal / goal.targetScore
                        } else {
                            0f
                        }
                        appendLine(
                            "- 最近一次（${latestExam?.name}）总分 " +
                                "${trim(latestTotal)}/${trim(fullTotal)}，" +
                                if (gap >= 0f) {
                                    "距总分目标还差 ${trim(gap)} 分"
                                } else {
                                    "已超出总分目标 ${trim(-gap)} 分"
                                } +
                                "（达成 ${"%.0f%%".format(rate * 100)}）"
                        )
                    }
                }

                if (subjectGoals.isNotEmpty()) {
                    appendLine("- 各科目标与最近一次对比：")
                    if (latestScores.isEmpty()) {
                        appendLine("  （还没有成绩可以对比）")
                    } else {
                        subjectGoals.forEach { goal ->
                            val subjectId = goal.subjectId ?: return@forEach
                            val name = subjectById[subjectId]?.name ?: return@forEach
                            val actual = latestScores.firstOrNull { it.subjectId == subjectId }
                                ?: return@forEach
                            val diff = goal.targetScore - actual.score
                            appendLine(
                                "  $name：目标 ${trim(goal.targetScore)}，" +
                                    "最近 ${trim(actual.score)}，" +
                                    if (diff > 0f) "差 ${trim(diff)} 分" else "已超出 ${trim(-diff)} 分"
                            )
                        }
                    }
                }
            }

            // ---------- 4. 错题统计 ----------
            appendLine()
            appendLine("【错题统计】")
            if (mistakes.isEmpty()) {
                appendLine("- 错题本为空")
            } else {
                val pending = mistakes.count { !it.isMastered }
                appendLine("- 错题总数：${mistakes.size} 道，其中待复习（未掌握）$pending 道")

                val bySubject = mistakes
                    .groupingBy { subjectById[it.subjectId]?.name ?: "未知科目" }
                    .eachCount()
                appendLine("- 按科目分布：" + bySubject.entries.joinToString("；") { "${it.key} ${it.value} 道" })

                val byStatus = mistakes.groupingBy { it.reviewStatus }.eachCount()
                appendLine("- 按复习状态：" + byStatus.entries.joinToString("；") { "${it.key} ${it.value} 道" })

                // 积压最多的科目值得单独点出来
                val worst = bySubject.maxByOrNull { it.value }
                if (worst != null && worst.value >= 3) {
                    appendLine("- 错题积压最多：${worst.key}（${worst.value} 道）")
                }
            }

            appendLine()
            appendLine("【错题举例】（最多 ${MAX_MISTAKES} 道）")
            mistakes.take(MAX_MISTAKES).forEach { mistake ->
                val subjectName = subjectById[mistake.subjectId]?.name ?: "未知科目"
                val content = mistake.content?.takeIf { it.isNotBlank() } ?: "（题目内容未填写）"
                val note = mistake.note?.takeIf { it.isNotBlank() }
                appendLine(
                    "- [$subjectName / ${mistake.reviewStatus}] $content" +
                        (note?.let { "｜错因：$it" } ?: "")
                )
            }
        }
    }

    private fun ScoreEntity.rateOrNull(): Double? =
        if (fullScore > 0f) (score / fullScore).toDouble() else null

    /** 用「最近一次」对比「之前几次的平均」，判断走势 */
    private fun describeTrend(rates: List<Double>): String {
        if (rates.size < 2) return "数据不足"
        val latest = rates.first()
        val previous = rates.drop(1).average()
        val delta = latest - previous
        return when {
            delta > 0.03 -> "上升（较此前平均 +${percent(delta)}）"
            delta < -0.03 -> "下降（较此前平均 ${percent(delta)}）"
            else -> "基本持平"
        }
    }

    private fun percent(value: Double?): String =
        if (value == null || value.isNaN()) "—" else "%.1f%%".format(value * 100)

    private fun trim(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}