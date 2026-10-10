package com.lingxi.scoretrack.data.local.model

/**
 * 错题详情投影。
 *
 * 错题本列表要显示"哪次考试的哪一科、第几题、错在哪"，
 * 而 mistake 表本身只存了 questionId —— 需要沿
 * mistake → question → paper → exam 一路 join 上来。
 * 这个 POJO 承接 join 结果，避免 UI 层反复查表。
 */
data class MistakeDetailRow(
    // --- mistake ---
    val mistakeId: Long,
    val note: String?,
    val reviewStatus: String,
    val reviewCount: Int,
    val lastReviewAt: Long?,
    // --- question ---
    val questionId: Long,
    val number: String,
    val type: String?,
    val content: String?,
    val answer: String?,
    val userAnswer: String?,
    val wrongReason: String?,
    val score: Float,
    val fullScore: Float,
    val difficulty: String?,
    // --- 上下文 ---
    val subjectId: Long,
    val paperTitle: String,
    val examName: String
) {
    val isMastered: Boolean
        get() = reviewStatus == "已掌握"
}