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
import com.lingxi.scoretrack.domain.usecase.GenerateReflectionUseCase
import com.lingxi.scoretrack.domain.usecase.ReflectionEntry
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
    private val examTypeCatalog: ExamTypeCatalog,
    private val generateReflectionUseCase: GenerateReflectionUseCase
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
                reflection = exam?.reflection.orEmpty(),
                aiReflection = exam?.aiReflection,
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

    fun updateReflection(value: String) = _uiState.update { it.copy(reflection = value) }

    /**
     * AI 建议生成成功就落库。
     *
     * 之前只在内存里，用户看到建议一高兴直接退出，下次打开空空如也 ——
     * 而这次调用是真花了 token 的。所以这里立刻写回 exam.aiReflection，
     * 只碰这一个字段，不动分数（用户可能还在改）。
     *
     * 新建考试还没有 id，只能等首次保存；那条路径由 [save] 负责带上。
     */
    private fun persistAiReflection(text: String) {
        val examId = editingExamId
        if (examId == 0L) return
        viewModelScope.launch {
            runCatching {
                val exam = examRepository.getById(examId) ?: return@launch
                examRepository.update(exam.copy(aiReflection = text))
                _uiState.update { it.copy(aiReflectionSaved = true) }
            }
        }
    }

    /**
     * 静默保存：不导航、不弹提示。
     *
     * 用在「返回」时。用户可能只是顺手退出，但他写下的反思是自己的东西，
     * 不该因为忘点保存键就没了。只在确实有反思内容时才写库，
     * 纯浏览不会产生任何写入。
     *
     * 新建（还没有 id）时什么都不做：留一条没名没姓的考试比丢了更糟。
     */
    fun saveSilently() {
        val state = _uiState.value
        if (!state.isEditing || state.isSaving) return
        if (state.reflection.isBlank() && state.aiReflection.isNullOrBlank()) return
        if (state.name.isBlank()) return
        save { }
    }

    fun dismissReflectError() = _uiState.update { it.copy(reflectError = null) }

    /**
     * 让 AI 基于「当前填的成绩 + 用户写的反思」给一段复盘建议。
     *
     * 不要求先保存 —— 用户刚录完分数时印象最深，这时候写反思、看建议
     * 才顺。所以入参用的是界面上的数据，不是 examId。
     *
     * 结果写进 [ExamEditUiState.aiReflection]，**不动**用户写的那段文字：
     * 用户的原话是他自己的东西，不该被模型的输出替换掉。
     */
    fun generateReflection() {
        val state = _uiState.value
        if (state.isReflecting) return

        val entries = state.entries.mapNotNull { entry ->
            val score = entry.score.trim().toFloatOrNull() ?: return@mapNotNull null
            ReflectionEntry(
                subjectId = entry.subjectId,
                subjectName = entry.subjectName,
                score = score,
                fullScore = entry.fullScore.trim().toFloatOrNull() ?: 0f
            )
        }
        if (entries.isEmpty()) {
            _uiState.update { it.copy(reflectError = "先填几科成绩，再来分析吧") }
            return
        }

        _uiState.update { it.copy(isReflecting = true, reflectError = null) }
        viewModelScope.launch {
            generateReflectionUseCase(
                examName = state.name.trim(),
                examType = state.type,
                entries = entries,
                reflection = state.reflection
            ).fold(
                onSuccess = { text ->
                    _uiState.update {
                        it.copy(isReflecting = false, aiReflection = text)
                    }
                    persistAiReflection(text)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isReflecting = false,
                            reflectError = error.message ?: "生成失败，请重试"
                        )
                    }
                }
            )
        }
    }

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
                remark = state.remark.trim().takeIf { it.isNotEmpty() },
                reflection = state.reflection.trim().takeIf { it.isNotEmpty() },
                aiReflection = state.aiReflection?.takeIf { it.isNotBlank() }
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