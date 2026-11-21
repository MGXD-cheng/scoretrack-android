package com.lingxi.scoretrack.ui.screen.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.domain.session.ProfileSession
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
    private val profileSession: ProfileSession
) : ViewModel() {

    private val profileId: StateFlow<Long> = profileSession.currentProfileId
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

    fun setFilter(value: MistakeFilter) {
        filter.update { value }
    }
}