package com.lingxi.scoretrack.ui.screen.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.repository.ProfileRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.domain.session.ProfileSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectManageUiState(
    val isLoading: Boolean = true,
    val profileId: Long = 0L,
    val profileName: String = "",
    val subjects: List<SubjectEntity> = emptyList()
) {
    val isEmpty: Boolean get() = !isLoading && subjects.isEmpty()
}

/**
 * 科目设置。
 *
 * 科目本来就是「每个档案一套」（subject 表带 profileId），
 * 之前只是没有入口去改 —— 新建档案时塞 9 门就再也动不了了。
 * 这里补上增删改和排序。
 *
 * 所有操作都作用于**当前档案**，切换档案后列表会跟着换 ——
 * 因为它订阅的是全局 [ProfileSession]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubjectManageViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val profileRepository: ProfileRepository,
    private val profileSession: ProfileSession
) : ViewModel() {

    val uiState: StateFlow<SubjectManageUiState> = profileSession.currentProfileId
        .flatMapLatest { id ->
            if (id <= 0L) {
                flowOf(SubjectManageUiState(isLoading = false))
            } else {
                combine(
                    profileRepository.observeById(id),
                    subjectRepository.observeByProfile(id)
                ) { profile, subjects ->
                    SubjectManageUiState(
                        isLoading = false,
                        profileId = id,
                        profileName = profile?.name.orEmpty(),
                        subjects = subjects
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SubjectManageUiState()
        )

    private val _message = MutableStateFlow<String?>(null)
    /** 校验失败 / 操作结果提示。非空时弹对话框 */
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _pendingDelete = MutableStateFlow<SubjectEntity?>(null)
    /** 待确认删除的科目 */
    val pendingDelete: StateFlow<SubjectEntity?> = _pendingDelete.asStateFlow()

    private val _deleteScoreCount = MutableStateFlow(0)
    /** 待删科目下已有的成绩条数 —— 0 表示删了也不丢数据 */
    val deleteScoreCount: StateFlow<Int> = _deleteScoreCount.asStateFlow()

    /**
     * 新建或修改科目。
     *
     * [existing] 为 null 表示新建。返回前做完全部校验，
     * 校验失败只弹提示、不写库 —— 不会出现「名字存进去了但满分没存」的半截状态。
     */
    fun save(
        existing: SubjectEntity?,
        rawName: String,
        rawFullScore: String,
        color: String
    ) {
        val state = uiState.value
        if (state.profileId <= 0L) return

        val name = rawName.trim()
        if (name.isEmpty()) {
            _message.value = "科目名不能为空"
            return
        }
        if (name.length > 20) {
            _message.value = "科目名太长了，20 个字以内"
            return
        }

        val fullScore = rawFullScore.trim().toFloatOrNull()
        if (fullScore == null || fullScore <= 0f) {
            _message.value = "满分要填一个大于 0 的数字"
            return
        }
        // 150 / 100 / 300 都合理，但没有哪个科目的满分超过 1000
        if (fullScore > 1000f) {
            _message.value = "满分好像填得有点大，1000 以内比较合理"
            return
        }

        val duplicated = state.subjects.any { subject ->
            subject.id != (existing?.id ?: 0L) && subject.name.equals(name, ignoreCase = true)
        }
        if (duplicated) {
            _message.value = "「${state.profileName}」里已经有「$name」了"
            return
        }

        viewModelScope.launch {
            if (existing == null) {
                subjectRepository.insert(
                    SubjectEntity(
                        profileId = state.profileId,
                        name = name,
                        fullScore = fullScore,
                        color = color,
                        sortOrder = subjectRepository.nextSortOrder(state.profileId)
                    )
                )
            } else {
                subjectRepository.update(
                    existing.copy(name = name, fullScore = fullScore, color = color)
                )
            }
        }
    }

    /**
     * 上移 / 下移。[delta] 为 -1 上移，+1 下移。
     *
     * 不做「交换 sort_order」而是整体重排，原因见 SubjectRepository.reorder。
     */
    fun move(subject: SubjectEntity, delta: Int) {
        val list = uiState.value.subjects
        val index = list.indexOfFirst { it.id == subject.id }
        if (index < 0) return
        val target = index + delta
        if (target !in list.indices) return

        val reordered = list.toMutableList().apply { add(target, removeAt(index)) }
        viewModelScope.launch { subjectRepository.reorder(reordered) }
    }

    fun requestDelete(subject: SubjectEntity) {
        _pendingDelete.value = subject
        _deleteScoreCount.value = 0
        viewModelScope.launch {
            _deleteScoreCount.value = subjectRepository.countScores(subject.id)
        }
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { subjectRepository.deleteWithScores(target) }
    }

    /** 把默认 9 科里缺的那些补回来（已有的同名科目跳过，不会覆盖用户改过的满分） */
    fun addMissingDefaults() {
        val state = uiState.value
        if (state.profileId <= 0L) return
        viewModelScope.launch {
            val existingNames = subjectRepository.getByProfile(state.profileId)
                .map { it.name }
                .toSet()
            val missing = ProfileRepository.defaultSubjects(state.profileId)
                .filterNot { it.name in existingNames }
            if (missing.isEmpty()) {
                _message.value = "默认科目都已经有了"
                return@launch
            }
            // 接在已有科目后面，不要插到最前面打乱用户排好的顺序
            val startOrder = subjectRepository.nextSortOrder(state.profileId)
            subjectRepository.insertAll(
                missing.mapIndexed { index, subject ->
                    subject.copy(sortOrder = startOrder + index)
                }
            )
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}