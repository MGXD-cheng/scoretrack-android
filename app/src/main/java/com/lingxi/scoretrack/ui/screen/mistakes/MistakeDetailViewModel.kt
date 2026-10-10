package com.lingxi.scoretrack.ui.screen.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.scoretrack.data.local.entity.KnowledgePointEntity
import com.lingxi.scoretrack.data.local.model.MistakeDetailRow
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.KnowledgePointRepository
import com.lingxi.scoretrack.data.repository.MistakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MistakeDetailViewModel @Inject constructor(
    private val mistakeRepository: MistakeRepository,
    private val knowledgePointRepository: KnowledgePointRepository,
    private val appSettingRepository: AppSettingRepository
) : ViewModel() {

    private val mistakeId = MutableStateFlow(0L)

    val detail: StateFlow<MistakeDetailRow?> = mistakeId
        .flatMapLatest { id ->
            if (id <= 0L) flowOf(null) else mistakeRepository.observeDetailById(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun init(id: Long) {
        mistakeId.value = id
    }

    /** 流转复习状态：未复习 → 复习中 → 已掌握（同时累加复习次数） */
    fun setStatus(status: String) {
        val id = mistakeId.value
        if (id <= 0L) return
        viewModelScope.launch {
            // 投影里没有完整实体，这里按 id 取一下再更新
            val entity = mistakeRepository.getById(id) ?: return@launch
            mistakeRepository.setReviewStatus(entity, status)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = mistakeId.value
        if (id <= 0L) return
        viewModelScope.launch {
            val entity = mistakeRepository.getById(id) ?: return@launch
            mistakeRepository.delete(entity)
            onDeleted()
        }
    }

    // ---------- 知识点手动归类 ----------

    private val _points = MutableStateFlow<List<KnowledgePointEntity>>(emptyList())

    /** 这道题当前挂着的知识点 */
    val points: StateFlow<List<KnowledgePointEntity>> = _points.asStateFlow()

    private var questionId: Long = 0L
    private var subjectId: Long = 0L

    /** 题目详情就绪后调用，绑定后才知道给哪道题归类 */
    fun bindQuestion(questionId: Long, subjectId: Long) {
        if (this.questionId == questionId) return
        this.questionId = questionId
        this.subjectId = subjectId
        loadPoints()
    }

    /**
     * 加一个知识点。
     *
     * 用户输入的名字如果库里没有，[KnowledgePointRepository.ensurePointAndLink]
     * 会顺手把它建出来 —— 不用先跑去别的地方「新建知识点」再回来选。
     */
    fun addPoint(name: String) {
        val trimmed = name.trim()
        val target = questionId
        if (target <= 0L || trimmed.isEmpty()) return

        viewModelScope.launch {
            val profileId = appSettingRepository.getLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                0L
            )
            knowledgePointRepository.ensurePointAndLink(
                profileId = profileId,
                subjectId = subjectId,
                questionId = target,
                name = trimmed
            )
            loadPoints()
        }
    }

    /** 解除关联。知识点本身留着 —— 别的题可能还在用它 */
    fun removePoint(knowledgePointId: Long) {
        val target = questionId
        if (target <= 0L) return
        viewModelScope.launch {
            knowledgePointRepository.unlink(target, knowledgePointId)
            loadPoints()
        }
    }

    private fun loadPoints() {
        val target = questionId
        if (target <= 0L) return
        viewModelScope.launch {
            val links = knowledgePointRepository.linksOfQuestion(target)
            _points.value = links.mapNotNull {
                knowledgePointRepository.getById(it.knowledgePointId)
            }
        }
    }
}