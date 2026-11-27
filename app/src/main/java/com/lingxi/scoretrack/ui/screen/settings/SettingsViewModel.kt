package com.lingxi.scoretrack.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.repository.ProfileRepository
import com.lingxi.scoretrack.domain.backup.BackupManager
import com.lingxi.scoretrack.BuildConfig
import com.lingxi.scoretrack.domain.session.ProfileSession
import com.lingxi.scoretrack.domain.update.UpdateChecker
import com.lingxi.scoretrack.domain.update.UpdateResult
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
    private val backupManager: BackupManager,
    private val profileSession: ProfileSession,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 当前档案。直接透传全局会话，不再自己存一份 —— 否则和别的页面会对不上。 */
    val currentProfileId: StateFlow<Long> = profileSession.currentProfileId

    fun switchProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileSession.switchTo(profile.id) }
    }

    fun createProfile(
        name: String,
        gradeName: String = "",
        className: String = "",
        remark: String = ""
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = profileRepository.createWithDefaultSubjects(
                name = trimmed,
                gradeName = gradeName.trim().takeIf { it.isNotEmpty() },
                className = className.trim().takeIf { it.isNotEmpty() },
                remark = remark.trim().takeIf { it.isNotEmpty() },
                isDefault = true
            )
            profileSession.switchTo(id)
        }
    }

    /**
     * 改档案资料。
     *
     * 只允许改这四个文本字段 —— `isDefault` 和 `createdAt` 不在编辑范围内。
     * 用 `copy()` 而不是重新 new 一个 Entity，就是为了**防止**以后给
     * ProfileEntity 加字段时，这里悄悄把它清成默认值（`avatarPath` 现在
     * 就是这么被保住的：虽然还没做头像功能，但改了名字也不会把它抹掉）。
     */
    fun updateProfile(
        profile: ProfileEntity,
        name: String,
        gradeName: String,
        className: String,
        remark: String
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            profileRepository.update(
                profile.copy(
                    name = trimmed,
                    gradeName = gradeName.trim().takeIf { it.isNotEmpty() },
                    className = className.trim().takeIf { it.isNotEmpty() },
                    remark = remark.trim().takeIf { it.isNotEmpty() }
                )
            )
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
            profileRepository.deleteCascade(target.id)
            // 删的可能正是当前档案。让 session 重新解析：存下的 id 已失效，
            // 它会自动回退到默认档案（没有档案则归零，首页回到引导态）。
            profileSession.syncFromStorage()
        }
    }

    // ---------- 备份 / 恢复 ----------

    private val _backupMessage = MutableStateFlow<String?>(null)

    /** 导出/恢复的结果提示。非空时界面弹一个对话框 */
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)

    /** 导出/恢复进行中 —— 期间要挡住重复点击 */
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    /** SAF 保存对话框的默认文件名 */
    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    fun exportBackup(uri: Uri) {
        if (_isBackupBusy.value) return
        _isBackupBusy.value = true
        viewModelScope.launch {
            val result = backupManager.export(uri)
            _backupMessage.value = if (result.isSuccess) {
                "已导出 ${result.recordCount} 条记录。\n" +
                    "文件可以拷到网盘或者另一台手机上，随时用它恢复。"
            } else {
                "导出失败：${result.error}"
            }
            _isBackupBusy.value = false
        }
    }

    fun restoreBackup(uri: Uri) {
        if (_isBackupBusy.value) return
        _isBackupBusy.value = true
        viewModelScope.launch {
            val result = backupManager.restore(uri)
            _backupMessage.value = if (result.isSuccess) {
                "恢复完成，新增或更新了 ${result.recordCount} 条记录。\n" +
                    "原有的数据没有被删除，重复的内容会自动跳过。"
            } else {
                "恢复失败：${result.error}"
            }
            _isBackupBusy.value = false
        }
    }

    fun dismissBackupMessage() {
        _backupMessage.value = null
    }

    // ---------- 检查更新 ----------
    private val _updateResult = MutableStateFlow<UpdateResult?>(null)
    /** 检查结果，非空时弹对话框 */
    val updateResult: StateFlow<UpdateResult?> = _updateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    fun checkUpdate() {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            _updateResult.value = updateChecker.check(BuildConfig.VERSION_NAME)
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdate() {
        _updateResult.value = null
    }
}