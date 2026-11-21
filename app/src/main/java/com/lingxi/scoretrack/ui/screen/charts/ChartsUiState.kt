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

/** 饼图的一片：某科目在最近一次考试里的失分 */
data class LossSlice(
    val subjectName: String,
    val loss: Float
)

/**
 * 热力图的一行：一个科目在历次考试中的得分率。
 *
 * [rates] 里的 null 表示那次考试**这一科没录成绩** —— 和「考了 0 分」
 * 必须区分开：前者画灰格子，后者画红格子。
 */
data class HeatRow(
    val subjectName: String,
    val rates: List<Float?>
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

    /**
     * 雷达图上的目标线：各科的「目标得分率」，与 [latestRatePoints] 的科目
     * **一一对应**（同样的遍历顺序、同样的过滤条件）—— 雷达图的两个数据集
     * 必须共用同一套轴标签，长度差一个就会整体错位。
     *
     * 没设目标的科目沿用实际值，两条线在那里自然重合。这是刻意的：
     * **宁可让线重合，也不凭空编一个目标分** —— 雷达图上凭空凹下去一块，
     * 比线重合要误导得多。
     */
    val latestTargetPoints: List<RatePoint>
        get() {
            if (subjectGoals.isEmpty()) return emptyList()
            val latest = exams.maxByOrNull { it.date } ?: return emptyList()
            val scores = scoresByExam[latest.id].orEmpty()
            val goalBySubjectId = subjectGoals.associate { (subject, goal) -> subject.id to goal }

            return subjects.mapNotNull { subject ->
                val score = scores.firstOrNull { it.subjectId == subject.id }
                    ?: return@mapNotNull null
                val goal = goalBySubjectId[subject.id]

                // 目标分按**科目满分**折算 —— 目标是在「这门课满分多少」的口径下
                // 填的，而当次考试的满分可能不一样（比如小测只算 100 分）
                val fullForGoal = subject.fullScore.takeIf { it > 0f } ?: score.fullScore

                RatePoint(
                    subjectName = subject.name,
                    rate = if (goal != null) {
                        scoreRate(goal.targetScore, fullForGoal)
                    } else {
                        scoreRate(score.score, score.fullScore)
                    }
                )
            }
        }

    val latestExamName: String
        get() = exams.maxByOrNull { it.date }?.name.orEmpty()

    // ---------- 饼图：失分构成 ----------

    /**
     * 最近一次考试各科的失分（满分 - 得分），按失分从多到少排序。
     *
     * 满分（失分 0）的科目不进饼图 —— 饼图讲的是「分丢在哪了」，
     * 掺进一片 0 只会让图例变长、占比被稀释。
     */
    val latestLossSlices: List<LossSlice>
        get() {
            val latest = exams.maxByOrNull { it.date } ?: return emptyList()
            val scores = scoresByExam[latest.id].orEmpty()
            return subjects
                .mapNotNull { subject ->
                    val score = scores.firstOrNull { it.subjectId == subject.id }
                        ?: return@mapNotNull null
                    val loss = score.fullScore - score.score
                    if (loss <= 0f) return@mapNotNull null
                    LossSlice(subjectName = subject.name, loss = loss)
                }
                .sortedByDescending { it.loss }
        }

    // ---------- 热力图：科目 × 考试 ----------

    /**
     * 热力图矩阵：行 = 科目，列 = 最近 [MAX_HEAT_EXAMS] 次考试。
     *
     * 列按时间**升序**（左边最早、右边最近），因为读的趋势是从左往右，
     * 跟折线图保持一致。
     */
    val heatMap: List<HeatRow>
        get() {
            val recent = recentExamsForHeat
            if (recent.isEmpty()) return emptyList()
            return subjects
                .map { subject ->
                    HeatRow(
                        subjectName = subject.name,
                        rates = recent.map { exam ->
                            scoresByExam[exam.id]
                                .orEmpty()
                                .firstOrNull { it.subjectId == subject.id }
                                ?.let { scoreRate(it.score, it.fullScore) }
                        }
                    )
                }
                // 一次成绩都没有的科目整行不显示：一行纯灰既占地方又不带信息。
                // 行内仍保留零星灰格 —— 那是「这次没考」，跟「整科没数据」不是一回事。
                .filter { row -> row.rates.any { it != null } }
        }

    /** 热力图的列标题，跟 [heatMap] 每行的 rates 一一对应 */
    val heatExamLabels: List<String>
        get() = recentExamsForHeat.map { it.name }

    /** 热力图只画最近几次考试 —— 再多格子就挤成一条线了 */
    private val recentExamsForHeat: List<ExamEntity>
        get() = exams.sortedBy { it.date }.takeLast(MAX_HEAT_EXAMS)

    private companion object {
        const val MAX_HEAT_EXAMS = 6
    }

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