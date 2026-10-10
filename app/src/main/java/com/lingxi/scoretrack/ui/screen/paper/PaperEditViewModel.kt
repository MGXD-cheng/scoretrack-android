package com.lingxi.scoretrack.ui.screen.paper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.PaperRepository
import com.lingxi.scoretrack.data.repository.QuestionRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.domain.model.QuestionDraft
import com.lingxi.scoretrack.domain.usecase.ExtractPaperQuestionsUseCase
import com.lingxi.scoretrack.domain.usecase.SavePaperWithQuestionsUseCase
import com.lingxi.scoretrack.ui.util.formatScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaperEditUiState(
    val isLoading: Boolean = true,
    val examId: Long = 0L,
    val subjectId: Long = 0L,
    val examName: String = "",
    val subjectName: String = "",
    val questions: List<QuestionDraft> = emptyList(),
    /** 同时只展开一道题：小题的字段不少，全展开会淹掉用户 */
    val expandedIndex: Int? = null,
    val isSaving: Boolean = false,
    /** 正在从上传的文件里提取题目 */
    val isExtracting: Boolean = false,
    val error: String? = null
) {
    val wrongCount: Int get() = questions.count { it.isWrong }

    val totalScore: Float
        get() = questions.sumOf { it.scoreValue.toDouble() }.toFloat()

    val totalFullScore: Float
        get() = questions.sumOf { it.fullScoreValue.toDouble() }.toFloat()

    val canSave: Boolean get() = !isLoading && !isSaving && questions.isNotEmpty()
}

@HiltViewModel
class PaperEditViewModel @Inject constructor(
    private val examRepository: ExamRepository,
    private val subjectRepository: SubjectRepository,
    private val paperRepository: PaperRepository,
    private val questionRepository: QuestionRepository,
    private val appSettingRepository: AppSettingRepository,
    private val savePaper: SavePaperWithQuestionsUseCase,
    private val extractPaperQuestions: ExtractPaperQuestionsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaperEditUiState())
    val uiState: StateFlow<PaperEditUiState> = _uiState.asStateFlow()

    fun init(examId: Long, subjectId: Long) {
        if (_uiState.value.examId == examId && _uiState.value.subjectId == subjectId &&
            !_uiState.value.isLoading
        ) {
            return
        }

        viewModelScope.launch {
            val exam = examRepository.getById(examId)
            val subject = subjectRepository.getById(subjectId)
            val paper = paperRepository.getByExamAndSubject(examId, subjectId)
            val existing = paper?.let { questionRepository.getByPaper(it.id) }.orEmpty()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    examId = examId,
                    subjectId = subjectId,
                    examName = exam?.name.orEmpty(),
                    subjectName = subject?.name.orEmpty(),
                    questions = existing.map { question ->
                        QuestionDraft(
                            id = question.id,
                            number = question.number,
                            fullScore = formatScore(question.fullScore),
                            score = formatScore(question.score),
                            difficulty = question.difficulty,
                            content = question.content.orEmpty(),
                            answer = question.answer.orEmpty(),
                            userAnswer = question.userAnswer.orEmpty(),
                            wrongReason = question.wrongReason.orEmpty()
                        )
                    }
                )
            }
        }
    }

    fun updateQuestion(index: Int, transform: (QuestionDraft) -> QuestionDraft) {
        _uiState.update { state ->
            if (index !in state.questions.indices) return@update state
            state.copy(
                questions = state.questions.mapIndexed { i, draft ->
                    if (i == index) transform(draft) else draft
                }
            )
        }
    }

    fun toggleExpand(index: Int) {
        _uiState.update { state ->
            state.copy(expandedIndex = if (state.expandedIndex == index) null else index)
        }
    }

    fun addQuestion() {
        _uiState.update { state ->
            val draft = QuestionDraft(
                number = QuestionDraft.nextNumberOf(state.questions),
                // 默认满分 10 分，是中学小题最常见的分值，省一次输入
                fullScore = "10",
                score = "10"
            )
            state.copy(
                questions = state.questions + draft,
                expandedIndex = state.questions.size
            )
        }
    }

    fun removeQuestion(index: Int) {
        _uiState.update { state ->
            if (index !in state.questions.indices) return@update state
            state.copy(
                questions = state.questions.filterIndexed { i, _ -> i != index },
                expandedIndex = null
            )
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    /**
     * 从上传的文件里提取小题并录入。
     *
     * **按题号对齐**：题号是同一道题的身份。
     * - 题号已存在 → 用识别结果更新它（分数、答案这些本来就是同一条记录的信息）
     * - 题号不认识 → 追加到末尾
     *
     * 这样重复上传同一份卷子只会刷新数据，不会把题目列表越堆越长。
     */
    fun importFrom(uri: Uri) {
        if (_uiState.value.isExtracting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExtracting = true, error = null) }

            extractPaperQuestions.invoke(uri)
                .onSuccess { drafts ->
                    if (drafts.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isExtracting = false,
                                error = "文件里没找到小题明细 —— 是不是只有各科总分？"
                            )
                        }
                        return@onSuccess
                    }

                    _uiState.update { state ->
                        val merged = state.questions.toMutableList()
                        val appended = mutableListOf<QuestionDraft>()
                        var updated = 0

                        drafts.forEach { draft ->
                            val key = draft.number.trim()
                            val index = if (key.isEmpty()) {
                                -1
                            } else {
                                merged.indexOfFirst { it.number.trim() == key }
                            }

                            if (index >= 0) {
                                // 保留原来的主键，否则保存时会被当成新题插入
                                merged[index] = draft.copy(id = merged[index].id)
                                updated++
                            } else {
                                appended += draft
                            }
                        }

                        state.copy(
                            isExtracting = false,
                            questions = merged + appended,
                            expandedIndex = null,
                            error = "识别到 ${drafts.size} 道题：" +
                                "更新 $updated 道、新增 ${appended.size} 道"
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            error = failure.message ?: "提取失败，换个文件或检查 AI 配置"
                        )
                    }
                }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val profileId = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )

            runCatching {
                savePaper.invoke(
                    profileId = profileId,
                    examId = state.examId,
                    subjectId = state.subjectId,
                    title = "${state.subjectName}试卷",
                    drafts = state.questions
                )
            }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                    onSaved()
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isSaving = false, error = failure.message ?: "保存失败，请重试")
                    }
                }
        }
    }
}