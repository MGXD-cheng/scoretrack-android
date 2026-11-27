package com.lingxi.scoretrack.widget

import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.repository.ExamGoalRepository
import com.lingxi.scoretrack.data.repository.ExamRepository
import com.lingxi.scoretrack.data.repository.ProfileRepository
import com.lingxi.scoretrack.data.repository.ScoreRepository
import com.lingxi.scoretrack.data.repository.SubjectRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 桌面小组件要显示的全部内容。
 *
 * 这里刻意只放**算好的结果**（总分、得分率、下一步该干什么），
 * 不放原始实体 —— 小队件的组合逻辑在 [WidgetDataLoader.build] 里，
 * 那是个纯函数，可以被单元测试按住。
 */
data class WidgetSnapshot(
    val hasProfile: Boolean = false,
    val profileName: String = "",
    val examName: String? = null,
    val totalScore: Float = 0f,
    val totalFull: Float = 0f,
    val targetTotal: Float? = null,
    val lowestSubject: String? = null,
    val lowestRate: Float? = null,
    val lowestSubjectTarget: Float? = null,
    val encouragement: String = "",
    val plan: String = ""
) {
    val hasExam: Boolean get() = totalFull > 0f

    val rate: Float? get() = if (totalFull > 0f) totalScore / totalFull else null

    /** 「578 / 750」这种，供界面直接用 */
    val scoreText: String
        get() = if (hasExam) "${format(totalScore)} / ${format(totalFull)}" else "暂无成绩"
}

/**
 * 激励语。
 *
 * 用「一年中的第几天」取而不是随机：同一自然日内无论刷新多少次都是同一句，
 * 换天才变。随机的话用户每滑一次桌面就换一句，像坏了。
 */
object Encouragement {

    /** 还没达标 / 还没有成绩 */
    private val PUSHING = listOf(
        "今天多弄懂一道题，考场就少慌一次。",
        "分数是结果，习惯才是原因 —— 先改一个小习惯。",
        "觉得晚的时候，恰恰是最早的时候。",
        "把「我做不到」换成「我还没做到」，差一个词，差一个学期。",
        "再难看的一张卷子，也能挑出一道值得重做的题。",
        "进步不是一天涨十分，是今天比昨天少错一道。",
        "你现在做的，是给三个月后的自己铺路。",
        "别急着跟同学比名次，先跟上次的自己比分数。",
        "累了可以歇十分钟，但别停三天。",
        "丢的那几分，是下次最该捡回来的东西。"
    )

    /** 已经达到目标 */
    private val KEEPING = listOf(
        "目标达成了 —— 现在把目标往上挪一档。",
        "稳住了，这就是复利的感觉。",
        "别松劲，领先是最容易被追上的位置。",
        "这次的分数，是上次那个没放弃的你换来的。",
        "保持节奏比冲刺更难，也更值钱。",
        "把这次的做法记下来，它比分数耐用。",
        "领先一点不算赢，领先一档才算稳。",
        "你已经在正确的坡道上了，继续走。",
        "考好了也别改计划，就按现在的节奏来。",
        "目标不是终点，是扶手。"
    )

    fun pick(dayOfYear: Int, onTrack: Boolean): String {
        val list = if (onTrack) KEEPING else PUSHING
        return list[Math.floorMod(dayOfYear, list.size)]
    }
}

/**
 * 「接下来的计划」。纯规则生成，不调用 AI。
 *
 * 小队件要的是**秒出、断网也在、不花钱**。每次刷新打一次模型的调用，
 * 既慢又可能失败，用户还会以为小组件坏了。AI 那套放在 App 里的
 * 「成绩反思」和「分析报告」里，各司其职。
 */
object PlanSuggestion {

    fun build(
        hasExam: Boolean,
        rate: Float?,
        targetTotal: Float?,
        totalScore: Float,
        lowestSubject: String?,
        lowestRate: Float?,
        lowestSubjectTarget: Float?
    ): String = when {
        !hasExam -> "先录一次成绩，这里就会告诉你下一步做什么。"

        targetTotal != null && totalScore >= targetTotal ->
            "总分目标已达成。下一步：把目标提高 ${format(maxOf(5f, targetTotal * 0.03f))} 分，再压一压。"

        lowestSubject != null && lowestSubjectTarget != null ->
            "下一步：把 $lowestSubject 拉到 ${format(lowestSubjectTarget)} 分 —— " +
                "每天 2 道${lowestSubject}专题，一周后回看。"

        lowestSubject != null && rate != null && rate < 0.6f ->
            "下一步：先保基础 —— 每天 20 分钟过 $lowestSubject 的课本例题，难题先放一放。"

        lowestSubject != null ->
            "下一步：主攻 $lowestSubject（这次得分率 ${percent(lowestRate)}）。" +
                "一天一个小节，别贪多。"

        targetTotal != null ->
            "离总分目标 ${format(targetTotal)} 还差点火候。挑最弱的一科，集中练两周。"

        else -> "还没有设目标。去「设置 → 考试目标」定一个总分，计划才有方向。"
    }
}

/**
 * 把数据库里的数据算成小组件要显示的内容。
 *
 * ## 为什么不用 ProfileSession
 * widget 常常是在**冷启动**时被渲染的（桌面进程拉起我们的 App 进程）。
 * ProfileSession 的初始值是 `0L`，真实值靠 `init` 里的异步 bootstrap 拿到 ——
 * 也就是说 widget 第一次渲染几乎必然读到 0，于是显示一张空卡片，
 * 得等下一次 update 才有内容。用户看到的就是「小组件加载不出来」。
 *
 * 所以这里直接读数据库：`isDefault = true` 的档案就是当前档案
 * （ProfileSession.switchTo 本来就会同步它），Room 的 Flow 冷启动可用。
 *
 * ## 为什么返回 Flow 而不是一次性快照
 * Glance 的 `update()` 只重跑 `provideContent`，**不会**重跑 `provideGlance`。
 * 在 provideContent 外面取快照，数据会被冻结在第一次渲染那一刻 ——
 * 之后刷新、改数据全都无效，而界面上完全看不出问题。
 */
@Singleton
class WidgetDataLoader @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val examRepository: ExamRepository,
    private val scoreRepository: ScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val examGoalRepository: ExamGoalRepository
) {

    /**
     * 读一次快照，供 provideGlance 直接使用。
     *
     * ## 为什么小组件不用「Flow + collectAsState」
     * 看起来最漂亮的做法是在 provideContent 里订阅 Room 的 Flow，
     * 让数据变化自动驱动重绘。但这条路在 Glance 里**走不通**：
     * Glance 的 composition 是给 RemoteViews 用的精简实现，
     * `LaunchedEffect` 这类副作用不会被可靠执行，而 `collectAsState`
     * 内部恰恰依赖它来收集 Flow —— 结果是组合永远停在初始值 /
     * 渲染不出第一帧，桌面上只剩一个转圈。
     *
     * 所以改成「取一次」，由系统按 `updatePeriodMillis` 定期重跑
     * provideGlance（半小时一次，对「看成绩」这个场景足够）。
     * App 回到前台时也会 updateAll 一次。
     */
    suspend fun loadOnce(dayOfYear: Int = LocalDate.now().dayOfYear): WidgetSnapshot {
        val profiles = profileRepository.getAllOnce()
        val profile = profiles.firstOrNull { it.isDefault } ?: profiles.firstOrNull()
            ?: return WidgetSnapshot()

        val latestExam = examRepository.getByProfile(profile.id).maxByOrNull { it.date }
        val subjects = subjectRepository.getByProfile(profile.id)
        val scores = latestExam?.let { scoreRepository.getByExam(it.id) }.orEmpty()
        val goals = examGoalRepository.observeByProfile(profile.id).first()

        return build(profile, latestExam, subjects, scores, goals, dayOfYear)
    }

    /** 计算逻辑委托给 [WidgetCalculator]：那是个纯对象，可以脱离 DI 单测。 */
    fun build(
        profile: ProfileEntity?,
        latestExam: ExamEntity?,
        subjects: List<SubjectEntity>,
        scores: List<ScoreEntity>,
        goals: List<ExamGoalEntity>,
        dayOfYear: Int
    ): WidgetSnapshot = WidgetCalculator.build(
        profile, latestExam, subjects, scores, goals, dayOfYear
    )

    companion object {
        /**
         * 小组件的代码跑在 launcher 的进程里，拿不到 Activity 的注入图。
         * 用 EntryPoint 从 Application 组件里取 —— Hilt 官方推荐的做法。
         */
        fun from(context: android.content.Context): WidgetDataLoader =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            ).widgetDataLoader()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetDataLoader(): WidgetDataLoader
}

object WidgetCalculator {
    /** 纯函数。不碰数据库、不碰时间 —— 测试可以直接喂参数。 */
    fun build(
        profile: ProfileEntity?,
        latestExam: ExamEntity?,
        subjects: List<SubjectEntity>,
        scores: List<ScoreEntity>,
        goals: List<ExamGoalEntity>,
        dayOfYear: Int
    ): WidgetSnapshot {
        if (profile == null) return WidgetSnapshot()

        val examScores = latestExam?.let { exam -> scores.filter { it.examId == exam.id } }.orEmpty()
        val total = examScores.fold(0f) { acc, item -> acc + item.score }
        val full = examScores.fold(0f) { acc, item -> acc + item.fullScore }

        val subjectById = subjects.associateBy { it.id }
        val totalGoal = goals.firstOrNull { it.subjectId == null }?.targetScore
        val goalBySubject = goals.mapNotNull { goal -> goal.subjectId?.let { it to goal.targetScore } }.toMap()

        // 最弱的一科按**得分率**挑，不是按分数：80/100 和 120/150 直接比分数是错的
        data class Weakest(val name: String, val rate: Float, val target: Float?)

        val weakest = examScores.mapNotNull { score ->
            val subject = subjectById[score.subjectId] ?: return@mapNotNull null
            if (score.fullScore <= 0f) return@mapNotNull null
            Weakest(subject.name, score.score / score.fullScore, goalBySubject[subject.id])
        }.minByOrNull { it.rate }

        val rate = if (full > 0f) total / full else null
        val onTrack = totalGoal != null && total >= totalGoal

        return WidgetSnapshot(
            hasProfile = true,
            profileName = profile.name,
            examName = latestExam?.name,
            totalScore = total,
            totalFull = full,
            targetTotal = totalGoal,
            lowestSubject = weakest?.name,
            lowestRate = weakest?.rate,
            lowestSubjectTarget = weakest?.target,
            encouragement = Encouragement.pick(dayOfYear, onTrack),
            plan = PlanSuggestion.build(
                hasExam = full > 0f,
                rate = rate,
                targetTotal = totalGoal,
                totalScore = total,
                lowestSubject = weakest?.name,
                lowestRate = weakest?.rate,
                lowestSubjectTarget = weakest?.target
            )
        )
    }
}

/** 去掉没意义的小数尾巴：578.0 -> 578，89.5 -> 89.5 */
internal fun format(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)

internal fun percent(rate: Float?): String =
    if (rate == null) "未知" else "${(rate * 100).toInt()}%"