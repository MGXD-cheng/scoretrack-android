package com.lingxi.scoretrack.ui.screen.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.domain.session.ProfileSession
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.ScoreRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScoresViewModel @Inject constructor(
    private val examRepository: ExamRepository,
    private val scoreRepository: ScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val profileSession: ProfileSession
) : ViewModel() {

    private val profileId: StateFlow<Long> = profileSession.currentProfileId

    val uiState: StateFlow<ScoresUiState> = profileId
        .flatMapLatest { id ->
            if (id <= 0L) {
                flowOf(ScoresUiState(isLoading = false))
            } else {
                combine(
                    examRepository.observeByProfile(id),
                    subjectRepository.observeByProfile(id),
                    scoreRepository.observeByProfile(id)
                ) { exams, subjects, scores ->
                    ScoresUiState(
                        isLoading = false,
                        exams = exams,
                        subjects = subjects,
                        scoresByExam = scores.groupBy { it.examId }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScoresUiState()
        )

    // ---------- 删除考试 ----------
    // 走二次确认。理由不是「怕误触」这么表面 —— 而是删除会在一个事务里
    // 连带清掉这场考试下面的试卷和题目，用户完全看不到
    // 这条链。所以确认框必须先把数字摆出来，让人知道自己在删什么。

    private val _deleteTarget = MutableStateFlow<ExamEntity?>(null)

    /** 待确认删除的考试，非空时弹确认框 */
    val deleteTarget: StateFlow<ExamEntity?> = _deleteTarget.asStateFlow()

    private val _deleteCascade = MutableStateFlow<ExamRepository.ExamCascadeCount?>(null)

    /**
     * 待删考试会连带删掉的附属数据量。
     *
     * 单独一个流、可变可空：统计是异步查出来的，界面要能显示「正在算」，
     * 而不是先编一个 0 出来让用户以为没有附属数据。
     */
    val deleteCascade: StateFlow<ExamRepository.ExamCascadeCount?> = _deleteCascade.asStateFlow()

    fun requestDelete(exam: ExamEntity) {
        _deleteTarget.value = exam
        _deleteCascade.value = null
        viewModelScope.launch {
            _deleteCascade.value = examRepository.countCascade(exam.id)
        }
    }

    fun cancelDelete() {
        _deleteTarget.value = null
        _deleteCascade.value = null
    }

    fun confirmDelete() {
        val target = _deleteTarget.value ?: return
        cancelDelete()
        viewModelScope.launch { examRepository.deleteWithCascade(target) }
    }
}