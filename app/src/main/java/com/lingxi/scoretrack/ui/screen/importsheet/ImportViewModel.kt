package com.lingxi.scoretrack.ui.screen.importsheet

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.file.DocumentExtractor
import com.lingxi.scoretrack.data.repository.AiRepository
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.domain.model.RecognizeResult
import com.lingxi.scoretrack.domain.model.RecognizedExam
import com.lingxi.scoretrack.domain.model.RecognizedSubject
import com.lingxi.scoretrack.domain.usecase.CommitRecognizedExamUseCase
import com.lingxi.scoretrack.domain.usecase.GenerateKnowledgePointsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ImportStage { Idle, Recognizing, Preview, Done }

data class ImportUiState(
    val stage: ImportStage = ImportStage.Idle,
    val imageUri: String? = null,
    val preview: RecognizedExam? = null,
    val rawResponse: String = "",
    /** 原始文件类型：image / pdf / csv / xlsx / docx，落库时记进 import_task */
    val fileType: String = "image",
    /**
     * 多人文件里当前选中第几个人。
     *
     * 单人文件（[RecognizedExam.detectedStudents] 为空）时这个值没意义，
     * 恒为 0，UI 也不会显示选择器。
     */
    val selectedStudentIndex: Int = 0,
    /** 自动归纳的结果提示。为 null 表示没跑（开关没开）或还在跑 */
    val autoCategorizeMessage: String? = null,
    val error: String? = null,
    val isImporting: Boolean = false,
    val importedExamId: Long? = null
) {
    val canImport: Boolean
        get() = preview != null && !preview.isEmpty && preview.examName.isNotBlank()
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiRepository: AiRepository,
    private val appSettingRepository: AppSettingRepository,
    private val commitUseCase: CommitRecognizedExamUseCase,
    private val generateKnowledgePoints: GenerateKnowledgePointsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * 统一入口：图片 / PDF 走视觉通道，CSV / Excel / Word 走文本通道。
     *
     * 分派逻辑在 [DocumentExtractor] 里，ViewModel 只负责
     * 「拿到提取结果 → 选对应的 AI 通道 → 更新状态」。
     */
    fun onSourcePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(stage = ImportStage.Recognizing, error = null, imageUri = uri.toString())
            }

            // 文件解析 / 图片压缩都是 CPU + IO 密集的，别占着主线程
            val extracted = withContext(Dispatchers.IO) {
                runCatching { DocumentExtractor.extract(context, uri) }
            }

            val source = extracted.getOrElse { failure ->
                _uiState.update {
                    it.copy(stage = ImportStage.Idle, error = failure.message ?: "文件读取失败")
                }
                return@launch
            }

            // 在入口就分流，别让后面的代码再判断一次
            val result = when (source) {
                is DocumentExtractor.Extracted.Text -> {
                    _uiState.update { it.copy(fileType = source.fileType) }
                    aiRepository.extractExamFromText(source.content)
                }

                is DocumentExtractor.Extracted.Images -> {
                    _uiState.update { it.copy(fileType = source.fileType) }
                    aiRepository.recognizeExamSheet(source.dataUrls)
                }
            }

            result
                .onSuccess { recognized ->
                    _uiState.update {
                        it.copy(
                            stage = ImportStage.Preview,
                            preview = recognized.exam,
                            rawResponse = recognized.rawResponse
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            stage = ImportStage.Idle,
                            error = error.message ?: "识别失败，请重试"
                        )
                    }
                }
        }
    }

    fun updateExamName(name: String) = mutatePreview { it.copy(examName = name) }

    /**
     * 切换要导入的学生。
     *
     * 直接把 [RecognizedExam.subjects] 换成这个人的成绩 —— 下游（确认页、
     * 落库）看到的永远只是「当前这个人」，不需要知道多人的存在。
     *
     * 用户之前对前一个人的编辑，已由 [mutateSubjects] 存回候选名单，切回去还在。
     */
    fun selectStudent(index: Int) {
        val exam = _uiState.value.preview ?: return
        val student = exam.detectedStudents.getOrNull(index) ?: return
        _uiState.update { state ->
            state.copy(
                selectedStudentIndex = index,
                preview = exam.copy(subjects = student.subjects)
            )
        }
    }

    fun updateSubjectScore(index: Int, raw: String) = mutateSubjects { subjects ->
        val value = raw.toFloatOrNull() ?: 0f
        subjects.mapIndexed { i, subject ->
            if (i == index) subject.copy(score = value) else subject
        }
    }

    fun updateSubjectFullScore(index: Int, raw: String) = mutateSubjects { subjects ->
        val value = raw.toFloatOrNull() ?: subjectFullScoreFallback(subjects, index)
        subjects.mapIndexed { i, subject ->
            if (i == index) subject.copy(fullScore = value) else subject
        }
    }

    fun removeSubject(index: Int) = mutateSubjects { subjects ->
        subjects.filterIndexed { i, _ -> i != index }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun backToIdle() = _uiState.update {
        it.copy(
            stage = ImportStage.Idle,
            preview = null,
            error = null,
            // 选中的学生也要复位，否则下一次识别多人文件会默认选中上一次的位置
            selectedStudentIndex = 0,
            // 上一次的归纳提示不能带到下一次导入里
            autoCategorizeMessage = null
        )
    }

    /** 确认导入：落库成功后把 examId 抛给 UI，由导航打开考试详情 */
    fun confirm() {
        val state = _uiState.value
        val preview = state.preview ?: return
        if (!state.canImport) return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }

            val profileId = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
            if (profileId <= 0L) {
                _uiState.update { it.copy(isImporting = false, error = "请先创建一个档案") }
                return@launch
            }

            runCatching {
                commitUseCase.invoke(
                    profileId = profileId,
                    result = RecognizeResult(exam = preview, rawResponse = state.rawResponse),
                    sourceType = if (state.fileType == "image" || state.fileType == "pdf") "photo" else "file",
                    sourceFileUrl = state.imageUri,
                    fileType = state.fileType,
                    // 多人文件里记下用户最终选了谁 —— 出问题时能一眼看出
                    // 「数据错了」还是「选错人了」
                    filterTarget = preview.detectedStudents
                        .getOrNull(state.selectedStudentIndex)
                        ?.name
                )
            }
                .onSuccess { examId ->
                    _uiState.update {
                        it.copy(stage = ImportStage.Done, isImporting = false, importedExamId = examId)
                    }
                    // 落库已经成功，归纳是「锦上添花」——放最后，失败了也不影响导入
                    maybeAutoCategorize(profileId)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = error.message ?: "导入失败，请重试"
                        )
                    }
                }
        }
    }

    /**
     * 导入完成后，如果开了「自动归纳知识点」，就顺手把这件事做了。
     *
     * **刻意不阻塞 UI**：归纳要跑一次 AI 调用，可能十几秒。用户此刻只想
     * 看到「导入完成」，不该被这个拖住 —— 所以它是 fire-and-forget，
     * 结果通过 [ImportUiState.autoCategorizeMessage] 异步回填。
     */
    private fun maybeAutoCategorize(profileId: Long) {
        viewModelScope.launch {
            val enabled = appSettingRepository.getBoolean(
                AppSettingRepository.KEY_AUTO_KNOWLEDGE,
                false
            )
            if (!enabled) return@launch

            // 注意：invoke 本身已经返回 Result，这里**不要再套 runCatching** ——
            // 否则拿到的是 Result<Result<T>>，fold 出来的"summary"其实是 Result。
            val result = generateKnowledgePoints.invoke(profileId)
            _uiState.update { state ->
                state.copy(
                    autoCategorizeMessage = result.fold(
                        onSuccess = { summary ->
                            val total = summary.newPointCount + summary.reusedPointCount
                            if (total == 0) {
                                "已自动归纳知识点，但没有发现新的薄弱点"
                            } else {
                                "已自动归纳 $total 个知识点" +
                                    "（新增 ${summary.newPointCount}）"
                            }
                        },
                        // 归纳失败不该影响「导入成功」这个事实，所以只提示不报错
                        onFailure = { "知识点自动归纳失败：${it.message}" }
                    )
                )
            }
        }
    }

    private fun mutatePreview(transform: (RecognizedExam) -> RecognizedExam) =
        _uiState.update { state ->
            val current = state.preview ?: return@update state
            state.copy(preview = transform(current))
        }

    /**
     * 改当前这个人的科目列表。
     *
     * **同时回写到候选名单**：不然「改张三的分数 → 切到李四 → 切回张三」，
     * 改动就凭空消失了 —— 用户会以为自己在改另一个人。
     */
    private fun mutateSubjects(transform: (List<RecognizedSubject>) -> List<RecognizedSubject>) {
        val index = _uiState.value.selectedStudentIndex
        mutatePreview { exam ->
            val updated = transform(exam.subjects)
            exam.copy(
                subjects = updated,
                detectedStudents = exam.detectedStudents.mapIndexed { i, student ->
                    if (i == index) student.copy(subjects = updated) else student
                }
            )
        }
    }

    private fun subjectFullScoreFallback(subjects: List<RecognizedSubject>, index: Int): Float =
        subjects.getOrNull(index)?.fullScore ?: 100f
}