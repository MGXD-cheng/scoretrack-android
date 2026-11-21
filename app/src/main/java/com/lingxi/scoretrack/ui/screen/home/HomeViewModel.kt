package com.lingxi.scoretrack.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.ProfileRepository
import com.lingxi.scoretrack.domain.session.ProfileSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val examRepository: ExamRepository,
    private val mistakeRepository: MistakeRepository,
    private val profileSession: ProfileSession
) : ViewModel() {

    private val currentProfileId = profileSession.currentProfileId

    val uiState: StateFlow<HomeUiState> = currentProfileId
        .flatMapLatest { id ->
            val allProfiles = profileRepository.observeAll()
            if (id <= 0L) {
                allProfiles.map { profiles ->
                    HomeUiState(isLoading = false, profiles = profiles)
                }
            } else {
                combine(
                    allProfiles,
                    profileRepository.observeById(id),
                    examRepository.observeLatest(id),
                    mistakeRepository.observePendingCount(id)
                ) { profiles, profile, latestExam, pending ->
                    HomeUiState(
                        isLoading = false,
                        profiles = profiles,
                        currentProfile = profile,
                        latestExam = latestExam,
                        pendingMistakes = pending
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    /**
     * 切换档案。
     *
     * 只改 ProfileSession 这一处 —— 其余 9 个 ViewModel 订阅的都是同一个
     * StateFlow，会跟着一起重算，不用挨个通知，也就不会漏。
     */
    fun switchProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileSession.switchTo(profile.id) }
    }

    /** 新建档案：自动预置 9 门科目，并立即切换为当前档案 */
    fun createProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = profileRepository.createWithDefaultSubjects(
                name = trimmed,
                isDefault = true
            )
            profileSession.switchTo(id)
        }
    }
}