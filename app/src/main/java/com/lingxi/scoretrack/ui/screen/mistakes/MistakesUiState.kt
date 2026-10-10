package com.lingxi.scoretrack.ui.screen.mistakes

import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.local.model.MistakeDetailRow

enum class MistakeFilter(val label: String) {
    All("全部"),
    Pending("未复习"),
    Reviewing("复习中"),
    Mastered("已掌握")
}

data class MistakesUiState(
    val isLoading: Boolean = true,
    val all: List<MistakeDetailRow> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val filter: MistakeFilter = MistakeFilter.All
) {
    val filtered: List<MistakeDetailRow>
        get() = when (filter) {
            MistakeFilter.All -> all
            MistakeFilter.Pending -> all.filter { it.reviewStatus == "未复习" }
            MistakeFilter.Reviewing -> all.filter { it.reviewStatus == "复习中" }
            MistakeFilter.Mastered -> all.filter { it.isMastered }
        }

    val pendingCount: Int get() = all.count { !it.isMastered }

    val masteredCount: Int get() = all.count { it.isMastered }

    /** 一条错题都没有（区别于「筛选后为空」） */
    val isEmpty: Boolean get() = !isLoading && all.isEmpty()

    val isFilteredEmpty: Boolean
        get() = !isLoading && all.isNotEmpty() && filtered.isEmpty()

    fun subjectNameOf(subjectId: Long): String =
        subjects.firstOrNull { it.id == subjectId }?.name ?: "科目"
}