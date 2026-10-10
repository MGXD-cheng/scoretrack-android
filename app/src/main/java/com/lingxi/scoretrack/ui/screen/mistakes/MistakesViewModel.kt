package com.lingxi.scoretrack.ui.screen.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MistakesViewModel @Inject constructor(
    private val mistakeRepository: MistakeRepository,
    private val subjectRepository: SubjectRepository,
    private val appSettingRepository: AppSettingRepository
) : ViewModel() {

    private val profileId = MutableStateFlow(0L)
    private val filter = MutableStateFlow(MistakeFilter.All)

    val uiState: StateFlow<MistakesUiState> = combine(
        profileId.flatMapLatest { id ->
            if (id <= 0L) {
                flowOf(emptyList())
            } else {
                mistakeRepository.observeDetailByProfile(id)
            }
        },
        profileId.flatMapLatest { id ->
            if (id <= 0L) flowOf(emptyList()) else subjectRepository.observeByProfile(id)
        },
        filter
    ) { mistakes, subjects, currentFilter ->
        MistakesUiState(
            isLoading = false,
            all = mistakes,
            subjects = subjects,
            filter = currentFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MistakesUiState()
    )

    init {
        viewModelScope.launch {
            profileId.value = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
        }
    }

    fun setFilter(value: MistakeFilter) {
        filter.update { value }
    }
}