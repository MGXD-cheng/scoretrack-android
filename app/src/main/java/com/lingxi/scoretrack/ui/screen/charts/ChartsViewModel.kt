package com.lingxi.scoretrack.ui.screen.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.ScoreRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val examRepository: ExamRepository,
    private val scoreRepository: ScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val appSettingRepository: AppSettingRepository
) : ViewModel() {

    private val profileId = MutableStateFlow(0L)

    val uiState: StateFlow<ChartsUiState> = profileId
        .flatMapLatest { id ->
            if (id <= 0L) {
                flowOf(ChartsUiState(isLoading = false))
            } else {
                combine(
                    examRepository.observeByProfile(id),
                    subjectRepository.observeByProfile(id),
                    scoreRepository.observeByProfile(id)
                ) { exams, subjects, scores ->
                    ChartsUiState(
                        isLoading = false,
                        exams = exams,
                        subjects = subjects,
                        scoresByExam = scores.groupBy { it.examId }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChartsUiState()
        )

    init {
        viewModelScope.launch {
            profileId.value = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
        }
    }
}