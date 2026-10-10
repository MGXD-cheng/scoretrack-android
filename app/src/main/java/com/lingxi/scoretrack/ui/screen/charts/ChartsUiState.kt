package com.lingxi.scoretrack.ui.screen.charts

import com.lingxi.scoretrack.data.local.entity.ExamEntity
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
    val scoresByExam: Map<Long, List<ScoreEntity>> = emptyMap()
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
}