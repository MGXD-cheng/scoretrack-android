package com.lingxi.scoretrack.ui.screen.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.local.model.KnowledgePointRow
import com.lingxi.scoretrack.data.repository.KnowledgePointRepository
import com.lingxi.scoretrack.domain.session.ProfileSession
import com.lingxi.scoretrack.domain.usecase.GenerateKnowledgePointsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KnowledgeUiState(
    val isLoading: Boolean = true,
    val points: List<KnowledgePointRow> = emptyList(),
    /** 当前展开的知识点，同时只展开一个 */
    val expandedId: Long? = null,
    val expandedQuestions: List<QuestionEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val message: String? = null
) {
    /** 按科目分组，UI 直接拿去渲染分段列表 */
    val grouped: List<Pair<String, List<KnowledgePointRow>>>
        get() = points
            .groupBy { it.subjectName }
            .toList()
            .sortedWith(compareByDescending<Pair<String, List<KnowledgePointRow>>> { it.second.size }
                .thenBy { it.first })

    val totalPoints: Int get() = points.size

    val totalLinks: Int get() = points.sumOf { it.questionCount }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val knowledgePointRepository: KnowledgePointRepository,
    private val profileSession: ProfileSession,
    private val generateKnowledgePoints: GenerateKnowledgePointsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 订阅当前档案而不是读一次快照 —— 切换档案时 flatMapLatest
            // 会自动取消旧查询、用新 id 重新订阅，不用手动刷新
            profileSession.currentProfileId
                .flatMapLatest { id ->
                    if (id <= 0L) flowOf(emptyList())
                    else knowledgePointRepository.observeOverview(id)
                }
                .collect { rows ->
                    _uiState.update { it.copy(isLoading = false, points = rows) }
                }
        }
    }

    fun toggle(pointId: Long) {
        _uiState.update { state ->
            if (state.expandedId == pointId) {
                state.copy(expandedId = null, expandedQuestions = emptyList())
            } else {
                state.copy(expandedId = pointId, expandedQuestions = emptyList())
            }
        }

        if (_uiState.value.expandedId == pointId) {
            viewModelScope.launch {
                val questions = knowledgePointRepository.questionsOfPoint(pointId)
                // 用户可能已经点了另一个知识点，别把过期的结果塞进去
                _uiState.update { state ->
                    if (state.expandedId == pointId) {
                        state.copy(expandedQuestions = questions)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun generate() {
        if (_uiState.value.isGenerating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, message = null) }

            val profileId = profileSession.currentProfileId.value
            if (profileId <= 0L) {
                _uiState.update { it.copy(isGenerating = false, message = "请先创建一个档案") }
                return@launch
            }

            generateKnowledgePoints.invoke(profileId)
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            message = "扫了 ${summary.questionCount} 道错题，" +
                                "新归纳 ${summary.newPointCount} 个知识点、" +
                                "沿用 ${summary.reusedPointCount} 个，" +
                                "建立 ${summary.linkCount} 条关联"
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            message = failure.message ?: "归纳失败，请检查 AI 配置"
                        )
                    }
                }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }
}