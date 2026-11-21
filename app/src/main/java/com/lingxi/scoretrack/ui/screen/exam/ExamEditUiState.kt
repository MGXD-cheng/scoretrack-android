package com.lingxi.scoretrack.ui.screen.exam

import com.lingxi.scoretrack.domain.exam.ExamTypeCatalog

/**
 * 单科录入输入。字段都是字符串，方便直接双向绑定到 TextField。
 */
data class ScoreEntryInput(
    val subjectId: Long,
    val subjectName: String,
    val fullScore: String,
    val score: String = "",
    val classRank: String = "",
    val gradeRank: String = "",
    val classAvg: String = ""
) {
    val hasScore: Boolean
        get() = score.isNotBlank()
}

data class ExamEditUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val name: String = "",
    val type: String = ExamTypeCatalog.DEFAULT_TYPES.first(),
    val date: Long = System.currentTimeMillis(),
    val remark: String = "",
    val entries: List<ScoreEntryInput> = emptyList(),
    val isSaving: Boolean = false,
    /**
     * 可选的考试类型：默认 7 种 + 用户自建的。
     *
     * 注意它是**状态**而不是常量 —— 用户在录入页里新建一个类型之后，
     * chip 列表要立刻多出来一个，不需要退出重进。
     */
    val examTypes: List<String> = ExamTypeCatalog.DEFAULT_TYPES
) {
    /** 至少要有考试名称 + 一科成绩才允许保存 */
    val canSave: Boolean
        get() = name.isNotBlank() && entries.any { it.hasScore }

    val filledCount: Int
        get() = entries.count { it.hasScore }
}