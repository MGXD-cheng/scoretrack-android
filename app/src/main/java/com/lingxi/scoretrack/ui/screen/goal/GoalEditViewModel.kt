package com.lingxi.scoretrack.ui.screen.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.ExamGoalRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.ui.util.formatScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalEditUiState(
    val isLoading: Boolean = true,
    val profileId: Long = 0L,
    val totalRank: String = "",
    val subjects: List<SubjectEntity> = emptyList(),
    /** subjectId → 目标分（字符串，允许中间态） */
    val subjectInputs: Map<Long, String> = emptyMap(),
    val isSaving: Boolean = false
) {
    /**
     * 各科目标之和。
     *
     * 总分目标**不由用户手填** —— 手填两处必然会对不上账，
     * 与其让用户自己去维持一致性，不如直接算出来给他看。
     */
    val computedTotal: Float
        get() = subjects
            .mapNotNull { subjectInputs[it.id]?.toFloatOrNull() }
            .filter { it > 0f }
            .sum()

    val filledSubjectCount: Int
        get() = subjects.count { (subjectInputs[it.id]?.toFloatOrNull() ?: 0f) > 0f }
}

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    private val examGoalRepository: ExamGoalRepository,
    private val subjectRepository: SubjectRepository,
    private val appSettingRepository: AppSettingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalEditUiState())
    val uiState: StateFlow<GoalEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profileId = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )

            // 只读一次快照，**不订阅 Flow** ——
            // 这是编辑页，每次输入都有可能触发写库，订阅回来会把用户
            // 正在敲的半个数字冲掉。
            val goals = examGoalRepository.observeByProfile(profileId).first()
            val subjects = subjectRepository.observeByProfile(profileId).first()

            val total = goals.firstOrNull { it.subjectId == null }
            val subjectGoals = goals
                .mapNotNull { goal -> goal.subjectId?.let { it to goal.targetScore } }
                .toMap()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    profileId = profileId,
                    totalRank = total?.targetRank?.toString().orEmpty(),
                    subjects = subjects,
                    subjectInputs = subjects.associate { subject ->
                        subject.id to (subjectGoals[subject.id]?.let(::formatScore).orEmpty())
                    }
                )
            }
        }
    }

    fun updateTotalRank(value: String) = _uiState.update { it.copy(totalRank = value) }

    fun updateSubject(subjectId: Long, value: String) = _uiState.update { state ->
        state.copy(subjectInputs = state.subjectInputs + (subjectId to value))
    }

    /**
     * 保存。
     *
     * - 总分目标 = 各科目标之和，**自动算**，不读用户输入；
     * - 输入框留空 = **清除这一项的目标**，而不是「保存 0 分」——
     *   用户清空就是想取消，不该被强行理解成"目标 0 分"。
     */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            state.subjects.forEach { subject ->
                val value = state.subjectInputs[subject.id].orEmpty().toFloatOrNull()
                if (value != null && value > 0f) {
                    examGoalRepository.setSubjectGoal(state.profileId, subject.id, value)
                } else {
                    examGoalRepository.clearGoal(state.profileId, subject.id)
                }
            }

            // 各科都清空了，总分目标也得跟着清掉，否则会留下一个孤儿目标
            val total = state.computedTotal
            if (total > 0f) {
                examGoalRepository.setTotalGoal(
                    profileId = state.profileId,
                    targetScore = total,
                    targetRank = state.totalRank.toIntOrNull()?.takeIf { it > 0 }
                )
            } else {
                examGoalRepository.clearGoal(state.profileId, null)
            }

            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}