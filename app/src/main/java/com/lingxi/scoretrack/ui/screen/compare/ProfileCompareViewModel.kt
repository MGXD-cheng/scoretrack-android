package com.lingxi.scoretrack.ui.screen.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import com.lingxi.scoretrack.data.repository.ProfileRepository
import com.lingxi.scoretrack.data.repository.ScoreRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 单个档案的对比摘要。
 *
 * 全部字段都是「已经算好的数字」而不是原始实体 —— 界面拿到就能直接画，
 * 不用在 Compose 里再遍历一遍列表。
 */
data class ProfileSummary(
    val profileId: Long,
    val name: String,
    val isDefault: Boolean,
    val examCount: Int,
    val subjectCount: Int,
    val mistakeCount: Int,
    val pendingMistakes: Int,
    /** 历次考试得分率的平均（0~1）。没有成绩时为 null */
    val averageRate: Float?,
    val latestExamName: String?,
    val latestExamDate: Long?,
    val latestTotal: Float?,
    val latestTotalFull: Float?
) {
    val latestRate: Float?
        get() {
            val total = latestTotal ?: return null
            val full = latestTotalFull ?: return null
            return if (full > 0f) total / full else null
        }
}

data class CompareUiState(
    val isLoading: Boolean = true,
    val rows: List<ProfileSummary> = emptyList()
) {
    val isEmpty: Boolean get() = !isLoading && rows.isEmpty()

    /** 平均得分率最高的那个档案（并列时取第一个），用来打「领先」标记 */
    val bestProfileId: Long?
        get() = rows.filter { it.averageRate != null }
            .maxByOrNull { it.averageRate ?: 0f }
            ?.profileId

    val hasAnyScore: Boolean get() = rows.any { it.averageRate != null }
}

/**
 * 多档案横向对比。
 *
 * ## 为什么是「全部档案」而不是让用户勾选
 *
 * 档案数量本来就少（自己 + 一两个对比对象），勾选面板会让页面多一层交互，
 * 收益却几乎为零。全量展示，谁高谁低一眼就能看出来。
 *
 * ## 数据怎么来
 *
 * 每个档案各开一组 Flow（考试 / 成绩 / 科目 / 错题），互不干扰地汇总，
 * 然后用 `combine` 把它们拼成一行。任何一个档案的数据变了（比如刚录了
 * 一场新考试），整张对比表会自动重算 —— 不需要手动刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileCompareViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val examRepository: ExamRepository,
    private val scoreRepository: ScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val mistakeRepository: MistakeRepository
) : ViewModel() {

    val uiState: StateFlow<CompareUiState> = profileRepository.observeAll()
        .flatMapLatest { profiles ->
            if (profiles.isEmpty()) {
                flowOf(CompareUiState(isLoading = false))
            } else {
                combine(*profiles.map { summaryFlow(it) }.toTypedArray()) { rows ->
                    CompareUiState(
                        isLoading = false,
                        // 得分率高的排前面：对比表最常见的用法就是「看谁在前面」，
                        // 让它开屏就在第一行
                        rows = rows.toList().sortedByDescending { it.averageRate ?: -1f }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CompareUiState()
        )

    private fun summaryFlow(profile: ProfileEntity): Flow<ProfileSummary> = combine(
        examRepository.observeByProfile(profile.id),
        scoreRepository.observeByProfile(profile.id),
        subjectRepository.observeByProfile(profile.id),
        mistakeRepository.observeByProfile(profile.id)
    ) { exams, scores, subjects, mistakes ->
        val scoresByExam = scores.groupBy { it.examId }

        // 得分率按「整场考试」算：单科 90/150 和 45/100 没法直接比，
        // 换算成「这次考试拿到的分占满分的比例」才有可比性。
        val rates = scoresByExam.values.mapNotNull { list ->
            val full = list.sumOf { it.fullScore.toDouble() }
            if (full <= 0.0) null else list.sumOf { it.score.toDouble() } / full
        }

        val latestExam = exams.maxByOrNull { it.date }
        val latestScores = latestExam?.let { scoresByExam[it.id] }.orEmpty()

        ProfileSummary(
            profileId = profile.id,
            name = profile.name,
            isDefault = profile.isDefault,
            examCount = exams.size,
            subjectCount = subjects.size,
            mistakeCount = mistakes.size,
            // 「已掌握」之外都算待复习 —— 和错题页的口径保持一致
            pendingMistakes = mistakes.count { it.reviewStatus != "已掌握" },
            averageRate = if (rates.isEmpty()) null else (rates.average()).toFloat(),
            latestExamName = latestExam?.name,
            latestExamDate = latestExam?.date,
            latestTotal = if (latestScores.isEmpty()) {
                null
            } else {
                latestScores.sumOf { it.score.toDouble() }.toFloat()
            },
            latestTotalFull = if (latestScores.isEmpty()) {
                null
            } else {
                latestScores.sumOf { it.fullScore.toDouble() }.toFloat()
            }
        )
    }
}