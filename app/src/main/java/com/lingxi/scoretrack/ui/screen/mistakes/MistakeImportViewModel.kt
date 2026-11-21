package com.lingxi.scoretrack.ui.screen.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.repository.SubjectRepository
import com.lingxi.scoretrack.domain.importer.ImportMistakesUseCase
import com.lingxi.scoretrack.domain.importer.ImportSummary
import com.lingxi.scoretrack.domain.importer.MistakeTextParser
import com.lingxi.scoretrack.domain.importer.ParseOutcome
import com.lingxi.scoretrack.domain.session.ProfileSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 错题批量导入。
 *
 * 解析是**纯函数**且实时跑在每次输入上 —— 文本最多几百行，
 * 重算成本远低于一次数据库写入，换来的是「边打字边看到几道题能导入」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MistakeImportViewModel @Inject constructor(
    private val importMistakes: ImportMistakesUseCase,
    private val subjectRepository: SubjectRepository,
    private val profileSession: ProfileSession
) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    /** 当前档案有哪些科目 —— 用来提前告诉用户「数学」这种名字能不能对上 */
    val subjects: StateFlow<List<String>> = profileSession.currentProfileId
        .flatMapLatest { id ->
            if (id <= 0L) {
                flowOf(emptyList())
            } else {
                subjectRepository.observeByProfile(id)
                    .map { list -> list.map { it.name } }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val outcome: StateFlow<ParseOutcome> = _text
        .map { MistakeTextParser.parse(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ParseOutcome(emptyList(), emptyList())
        )

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _result = MutableStateFlow<ImportSummary?>(null)
    /** 导入完成后的结果摘要，非空时弹对话框 */
    val result: StateFlow<ImportSummary?> = _result.asStateFlow()

    fun updateText(value: String) {
        _text.value = value
    }

    fun fillSample() {
        _text.value = MistakeTextParser.SAMPLE
    }

    fun clear() {
        _text.value = ""
    }

    fun dismissResult() {
        _result.value = null
    }

    fun import(onSuccess: () -> Unit) {
        if (_isImporting.value) return
        val rows = outcome.value.rows
        if (rows.isEmpty()) return
        val profileId = profileSession.currentProfileId.value
        if (profileId <= 0L) {
            _result.value = ImportSummary(0, rows.size, emptyList(), emptyMap())
            return
        }

        _isImporting.value = true
        viewModelScope.launch {
            val summary = runCatching { importMistakes.invoke(profileId, rows) }
                .getOrElse {
                    ImportSummary(0, rows.size, emptyList(), emptyMap())
                }
            _isImporting.value = false
            _result.value = summary
            if (summary.imported > 0) {
                _text.value = ""
                onSuccess()
            }
        }
    }
}