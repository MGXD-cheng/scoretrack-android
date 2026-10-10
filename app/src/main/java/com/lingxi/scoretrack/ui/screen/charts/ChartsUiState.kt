package com.lingxi.scoretrack.ui.screen.charts

import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.ui.util.scoreRate

/** 折线图上的一个点：一次考试的总分与满分 */
data class TrendPoint(
    val label: String,
    val total: Float,
    val fullScore: Float
) {
    val rate: Float get() = scoreRate(total, fullScore)
}

/** 雷达图上的一个点：某科目的得分率 */
data class RatePoint(
    val subjectName: String,
    val rate: Float
)

data class ChartsUiState(
    val isLoading: Boolean = true,
    val exams: List<ExamEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val scoresByExam: Map<Long, List<ScoreEntity>> = emptyMap(),
    val goals: List<ExamGoalEntity> = emptyList(),
    val latestReport: AnalysisReportEntity? = null
) {
    val isEmpty: Boolean
        get() = !isLoading && exams.isEmpty()

    /** 折线图：按时间升序的历次考试总分 */
    val trendPoints: List<TrendPoint>
        get() = exams
            .sortedBy { it.date }
            .map { exam ->
                val scores = scoresByExam[exam.id].orEmpty()
                TrendPoint(
                    label = exam.name,
                    total = scores.sumOf { it.score.toDouble() }.toFloat(),
                    fullScore = scores.sumOf { it.fullScore.toDouble() }.toFloat()
                )
            }

    /** 雷达图：最近一次考试的各科得分率 */
    val latestRatePoints: List<RatePoint>
        get() {
            val latest = exams.maxByOrNull { it.date } ?: return emptyList()
            val scores = scoresByExam[latest.id].orEmpty()
            return subjects.mapNotNull { subject ->
                val score = scores.firstOrNull { it.subjectId == subject.id }
                    ?: return@mapNotNull null
                RatePoint(
                    subjectName = subject.name,
                    rate = scoreRate(score.score, score.fullScore)
                )
            }
        }

    val latestExamName: String
        get() = exams.maxByOrNull { it.date }?.name.orEmpty()

    // ---------- 考试目标 ----------

    /** 总分目标：用 subjectId == null 标识 */
    val totalGoal: ExamGoalEntity?
        get() = goals.firstOrNull { it.subjectId == null }

    /** 最近一次考试的总分 */
    val latestTotalScore: Float
        get() = trendPoints.lastOrNull()?.total ?: 0f

    val latestTotalFullScore: Float
        get() = trendPoints.lastOrNull()?.fullScore ?: 0f

    /**
     * 目标达成进度。
     *
     * 没有目标、或者还没有考试数据时返回 null —— **宁可让 UI 不显示，
     * 也不要显示一个凭空的 0%**，那会让用户以为考砸了。
     */
    val goalProgress: Float?
        get() {
            val goal = totalGoal ?: return null
            if (goal.targetScore <= 0f) return null
            if (exams.isEmpty()) return null
            return latestTotalScore / goal.targetScore
        }

    /** 距离目标还差多少分（已达成或没目标时为 null） */
    val goalGap: Float?
        get() {
            val goal = totalGoal ?: return null
            if (exams.isEmpty()) return null
            val gap = goal.targetScore - latestTotalScore
            return gap.takeIf { it > 0f }
        }

    /** 各科目标，按科目顺序 */
    val subjectGoals: List<Pair<SubjectEntity, ExamGoalEntity>>
        get() {
            val subjectById = subjects.associateBy { it.id }
            return goals
                .mapNotNull { goal ->
                    val subjectId = goal.subjectId ?: return@mapNotNull null
                    val subject = subjectById[subjectId] ?: return@mapNotNull null
                    subject to goal
                }
                .sortedBy { it.first.sortOrder }
        }
}