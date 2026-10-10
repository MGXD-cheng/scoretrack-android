package com.lingxi.scoretrack.ui.screen.scores

import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity

data class ScoresUiState(
    val isLoading: Boolean = true,
    val exams: List<ExamEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val scoresByExam: Map<Long, List<ScoreEntity>> = emptyMap()
) {
    val isEmpty: Boolean
        get() = !isLoading && exams.isEmpty()

    fun totalOf(examId: Long): Float =
        scoresByExam[examId]?.sumOf { it.score.toDouble() }?.toFloat() ?: 0f

    fun fullTotalOf(examId: Long): Float =
        scoresByExam[examId]?.sumOf { it.fullScore.toDouble() }?.toFloat() ?: 0f

    fun subjectNameOf(subjectId: Long): String =
        subjects.firstOrNull { it.id == subjectId }?.name ?: "科目"
}