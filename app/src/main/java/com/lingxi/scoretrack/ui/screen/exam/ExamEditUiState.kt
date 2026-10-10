package com.lingxi.scoretrack.ui.screen.exam

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
    val type: String = "月考",
    val date: Long = System.currentTimeMillis(),
    val remark: String = "",
    val entries: List<ScoreEntryInput> = emptyList(),
    val isSaving: Boolean = false
) {
    /** 至少要有考试名称 + 一科成绩才允许保存 */
    val canSave: Boolean
        get() = name.isNotBlank() && entries.any { it.hasScore }

    val filledCount: Int
        get() = entries.count { it.hasScore }

    companion object {
        val EXAM_TYPES = listOf("月考", "期中", "期末", "模拟", "随堂", "其他")
    }
}