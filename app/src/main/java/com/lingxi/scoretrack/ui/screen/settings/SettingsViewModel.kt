package com.lingxi.scoretrack.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val appSettingRepository: AppSettingRepository
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _currentProfileId = MutableStateFlow(0L)
    val currentProfileId: StateFlow<Long> = _currentProfileId.asStateFlow()

    init {
        viewModelScope.launch {
            _currentProfileId.value = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
        }
    }

    fun switchProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            appSettingRepository.putLong(AppSettingRepository.KEY_CURRENT_PROFILE_ID, profile.id)
            if (!profile.isDefault) {
                profileRepository.setDefault(profile.id)
            }
            _currentProfileId.value = profile.id
        }
    }

    fun createProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = profileRepository.createWithDefaultSubjects(name = trimmed, isDefault = true)
            appSettingRepository.putLong(AppSettingRepository.KEY_CURRENT_PROFILE_ID, id)
            _currentProfileId.value = id
        }
    }

    // ---------- 删除档案 ----------

    private val _pendingDelete = MutableStateFlow<ProfileEntity?>(null)

    /** 待确认删除的档案，非空时弹二次确认框 */
    val pendingDelete: StateFlow<ProfileEntity?> = _pendingDelete.asStateFlow()

    fun requestDelete(profile: ProfileEntity) {
        _pendingDelete.value = profile
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    /**
     * 确认删除：级联清除该档案全部数据；若删的是当前档案，
     * 自动切换到剩下的第一个（没有剩余则置 0，首页会回到引导创建状态）。
     */
    fun confirmDelete() {
        val target = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            val wasCurrent = target.id == _currentProfileId.value
            profileRepository.deleteCascade(target.id)
            if (wasCurrent) {
                val nextId = profileRepository.getAllOnce().firstOrNull()?.id ?: 0L
                appSettingRepository.putLong(AppSettingRepository.KEY_CURRENT_PROFILE_ID, nextId)
                _currentProfileId.value = nextId
            }
        }
    }
}