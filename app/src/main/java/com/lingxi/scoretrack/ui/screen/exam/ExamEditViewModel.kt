package com.lingxi.scoretrack.ui.screen.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.ScoreRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.ui.util.formatScore
import com.lingxi.scoretrack.domain.exam.ExamTypeCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExamEditViewModel @Inject constructor(
    private val examRepository: ExamRepository,
    private val scoreRepository: ScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val appSettingRepository: AppSettingRepository,
    private val examTypeCatalog: ExamTypeCatalog
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamEditUiState())
    val uiState: StateFlow<ExamEditUiState> = _uiState.asStateFlow()

    private var profileId: Long = 0L
    private var editingExamId: Long = 0L
    private var initialized = false

    /** examId = 0 表示新建 */
    fun init(examId: Long) {
        if (initialized) return
        initialized = true
        editingExamId = examId

        // 类型目录与 examId 无关，一次订阅常驻 —— 用户新建类型后
        // chip 列表立刻多一个，不用退出重进
        viewModelScope.launch {
            examTypeCatalog.allTypes.collect { types ->
                _uiState.update { state -> state.copy(examTypes = types) }
            }
        }

        viewModelScope.launch {
            profileId = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
            val subjects = subjectRepository.getByProfile(profileId)
            val exam = if (examId > 0L) examRepository.getById(examId) else null
            val existing = if (examId > 0L) scoreRepository.getByExam(examId) else emptyList()

            _uiState.value = ExamEditUiState(
                isLoading = false,
                isEditing = exam != null,
                name = exam?.name.orEmpty(),
                type = exam?.type ?: ExamTypeCatalog.DEFAULT_TYPES.first(),
                date = exam?.date ?: System.currentTimeMillis(),
                remark = exam?.remark.orEmpty(),
                entries = subjects.map { subject ->
                    val old = existing.firstOrNull { it.subjectId == subject.id }
                    ScoreEntryInput(
                        subjectId = subject.id,
                        subjectName = subject.name,
                        fullScore = formatScore(old?.fullScore ?: subject.fullScore),
                        score = old?.let { formatScore(it.score) }.orEmpty(),
                        classRank = old?.classRank?.toString().orEmpty(),
                        gradeRank = old?.gradeRank?.toString().orEmpty(),
                        classAvg = old?.classAvg?.let { formatScore(it) }.orEmpty()
                    )
                }
            )
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }

    fun updateType(value: String) = _uiState.update { it.copy(type = value) }

    private val _typeMessage = MutableStateFlow<String?>(null)
    /** 类型管理对话框里的提示（加成功 / 重名…） */
    val typeMessage: StateFlow<String?> = _typeMessage.asStateFlow()

    /** 新建自定义类型。成功后直接选中它 —— 用户刚打完字，多半就是要用 */
    fun addExamType(rawName: String) {
        viewModelScope.launch {
            val error = examTypeCatalog.add(rawName)
            val name = rawName.trim()
            if (error == null) {
                _uiState.update { it.copy(type = name) }
                _typeMessage.value = "已添加「$name」"
            } else {
                _typeMessage.value = error
            }
        }
    }

    fun removeExamType(name: String) {
        viewModelScope.launch { examTypeCatalog.remove(name) }
    }

    fun dismissTypeMessage() {
        _typeMessage.value = null
    }

    fun updateDate(value: Long) = _uiState.update { it.copy(date = value) }

    fun updateRemark(value: String) = _uiState.update { it.copy(remark = value) }

    fun updateEntry(index: Int, transform: (ScoreEntryInput) -> ScoreEntryInput) {
        _uiState.update { state ->
            val list = state.entries.toMutableList()
            if (index in list.indices) {
                list[index] = transform(list[index])
            }
            state.copy(entries = list)
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank() || state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val exam = ExamEntity(
                id = editingExamId,
                profileId = profileId,
                name = state.name.trim(),
                type = state.type,
                date = state.date,
                remark = state.remark.trim().takeIf { it.isNotEmpty() }
            )

            // 只保存填了分数的科目
            val scores = state.entries.mapNotNull { entry ->
                val scoreValue = entry.score.trim().toFloatOrNull() ?: return@mapNotNull null
                ScoreEntity(
                    examId = editingExamId,
                    subjectId = entry.subjectId,
                    score = scoreValue,
                    fullScore = entry.fullScore.trim().toFloatOrNull() ?: 0f,
                    classAvg = entry.classAvg.trim().toFloatOrNull(),
                    classRank = entry.classRank.trim().toIntOrNull(),
                    gradeRank = entry.gradeRank.trim().toIntOrNull()
                )
            }

            examRepository.saveExamWithScores(exam, scores)
            _uiState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}