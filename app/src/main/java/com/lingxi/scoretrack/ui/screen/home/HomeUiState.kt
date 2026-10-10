package com.lingxi.scoretrack.ui.screen.home

import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ProfileEntity

data class HomeUiState(
    val isLoading: Boolean = true,
    val profiles: List<ProfileEntity> = emptyList(),
    val currentProfile: ProfileEntity? = null,
    val latestExam: ExamEntity? = null,
    val pendingMistakes: Int = 0
) {
    /** 没有任何档案 —— 走引导创建流程 */
    val isEmpty: Boolean
        get() = !isLoading && currentProfile == null
}