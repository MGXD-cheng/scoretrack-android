package com.lingxi.scoretrack.ui.screen.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.repository.AnalysisReportRepository
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.domain.usecase.GenerateAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val analysisReportRepository: AnalysisReportRepository,
    private val appSettingRepository: AppSettingRepository,
    private val generateAnalysis: GenerateAnalysisUseCase
) : ViewModel() {

    private val profileId = MutableStateFlow(0L)

    val reports: StateFlow<List<AnalysisReportEntity>> = profileId
        .flatMapLatest { id ->
            if (id <= 0L) flowOf(emptyList()) else analysisReportRepository.observeByProfile(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            profileId.value = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
        }
    }

    fun generate() {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.update { true }
            _error.update { null }

            // UseCase 的 invoke 没有标 operator，所以显式调用
            generateAnalysis.invoke(profileId.value)
                .onSuccess { reportId ->
                    // 生成成功直接打开，不用用户再去列表里找
                    _newReportId.update { reportId }
                }
                .onFailure { failure ->
                    _error.update { failure.message ?: "生成失败，请重试" }
                }

            _isGenerating.update { false }
        }
    }

    private val _newReportId = MutableStateFlow<Long?>(null)

    /** 新生成的报告 id，UI 消费后要调用 [consumeNewReport] 清掉，避免重复导航 */
    val newReportId: StateFlow<Long?> = _newReportId.asStateFlow()

    fun consumeNewReport() {
        _newReportId.update { null }
    }

    fun dismissError() {
        _error.update { null }
    }

    fun delete(report: AnalysisReportEntity) {
        viewModelScope.launch { analysisReportRepository.delete(report) }
    }
}