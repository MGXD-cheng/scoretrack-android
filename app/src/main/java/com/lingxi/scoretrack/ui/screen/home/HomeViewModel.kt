package com.lingxi.scoretrack.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.ProfileRepository
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
    private val appSettingRepository: AppSettingRepository
) : ViewModel() {

    private val currentProfileId = MutableStateFlow(0L)

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

    init {
        viewModelScope.launch {
            val stored = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
            val resolved = if (stored > 0L) {
                stored
            } else {
                val fallback = profileRepository.getDefault()
                    ?: profileRepository.observeAll().first().firstOrNull()
                fallback?.id ?: 0L
            }
            currentProfileId.value = resolved
            if (resolved > 0L) {
                appSettingRepository.putLong(
                    AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                    resolved
                )
            }
        }
    }

    fun switchProfile(profile: ProfileEntity) {
        currentProfileId.value = profile.id
        viewModelScope.launch {
            appSettingRepository.putLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                profile.id
            )
            if (!profile.isDefault) {
                profileRepository.setDefault(profile.id)
            }
        }
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
            currentProfileId.value = id
            appSettingRepository.putLong(AppSettingRepository.KEY_CURRENT_PROFILE_ID, id)
        }
    }
}