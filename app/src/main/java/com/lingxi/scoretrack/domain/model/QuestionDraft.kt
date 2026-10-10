package com.lingxi.scoretrack.domain.model

/**
 * 一道小题的草稿（UI 直接编辑的形态）。
 *
 * 所有数值字段都是 String —— 因为输入框允许用户把内容删空、
 * 允许中途只打了个减号。**只在真正用到时才解析**，
 * 不做「输入即校验」，否则用户打字打到一半就被吞字符。
 */
data class QuestionDraft(
    /** 0 表示新题 */
    val id: Long = 0L,
    val number: String = "",
    val fullScore: String = "",
    val score: String = "",
    val difficulty: String? = null,
    val content: String = "",
    val answer: String = "",
    val userAnswer: String = "",
    val wrongReason: String = ""
) {
    val scoreValue: Float get() = score.toFloatOrNull() ?: 0f

    val fullScoreValue: Float get() = fullScore.toFloatOrNull() ?: 0f

    /**
     * 错题判定：**有满分、且得分低于满分**。
     *
     * 满分没填时不判定 —— 宁可漏判也不要误伤：
     * 把一道没录满分的题当成错题塞进错题本，用户会很恼火。
     */
    val isWrong: Boolean get() = fullScoreValue > 0f && scoreValue < fullScoreValue

    companion object {
        /** 按顺序生成题号，减少手动输入 */
        fun nextNumberOf(existing: List<QuestionDraft>): String =
            (existing.size + 1).toString()
    }
}

/** 题型选项，和 ExamEditScreen 的类型选择保持一致的交互习惯 */
val DIFFICULTY_OPTIONS = listOf("易", "中", "难")