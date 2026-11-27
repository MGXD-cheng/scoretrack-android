package com.lingxi.scoretrack.domain.sync

import com.lingxi.scoretrack.data.local.AppDatabase
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.repository.AppSettingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把本机数据打包成一个 [SyncSnapshot]。
 *
 * ## 为什么按 profile 逐个查，而不是一条大 SQL
 * 每个档案下的数据量都不大（几十次考试 × 几门科目），但**表之间的级联关系
 * 很复杂**。按 profile 分块查的逻辑一眼就能看懂，而出一个跨 8 张表的
 * UNION 查询，调试起来是灾难。这里优先选可读性。
 *
 * ## 性能上的两个刻意选择
 * - 成绩 / 试卷 / 目标 / 知识点关联都用一次 JOIN 拿全（DAO 里的
 *   `getByProfileOnce`），而不是「先查考试列表，再逐个考试查成绩」——
 *   后者在用了两年的档案上会退化成上千次数据库往返。
 * - 题目只能按 paper 查（没有更好的维度），但试卷数量远小于题目数量，
 *   这个 N+1 是可以接受的。
 */
@Singleton
class SnapshotExporter @Inject constructor(
    private val db: AppDatabase,
    private val deviceNameProvider: DeviceNameProvider
) {

    /**
     * 导出全部档案。
     *
     * 「换手机」这个场景下用户想要的就是全部数据，所以没有做
     * 「导出单个档案」的入口 —— 少一个选择，少一份困惑。
     */
    suspend fun exportAll(): SyncSnapshot {
        val dao = db
        val profiles = dao.profileDao().getAllOnce()

        val subjects = mutableListOf<SyncSubject>()
        val exams = mutableListOf<SyncExam>()
        val goals = mutableListOf<SyncExamGoal>()
        val scores = mutableListOf<SyncScore>()
        val papers = mutableListOf<SyncPaper>()
        val questions = mutableListOf<SyncQuestion>()
        val reports = mutableListOf<SyncAnalysisReport>()

        profiles.forEach { profile ->
            val pid = profile.id

            subjects += dao.subjectDao().getByProfile(pid).map { it.toSync() }
            exams += dao.examDao().getByProfile(pid).map { it.toSync() }
            goals += dao.examGoalDao().getByProfileOnce(pid).map { it.toSync() }
            scores += dao.scoreDao().getByProfileOnce(pid).map { it.toSync() }
            reports += dao.analysisReportDao().getByProfile(pid).map { it.toSync() }

            // 试卷要先收集完，题目再按试卷逐个取
            val profilePapers = dao.paperDao().getByProfileOnce(pid)
            papers += profilePapers.map { it.toSync() }
            profilePapers.forEach { paper ->
                questions += dao.questionDao().getByPaper(paper.id).map { it.toSync() }
            }
        }

        // 用户偏好也带上。
        // 之前漏了 app_setting 这张表：自定义考试类型、自动归纳开关都存在里面，
        // 换手机之后一起没了 —— 而界面上完全看不出它「曾经有」。这种静默丢失
        // 比崩溃更难查，因为用户只会觉得「我是不是记错了」。
        val settings = dao.appSettingDao().getAllOnce()
            .filterNot { it.key in LOCAL_ONLY_KEYS }
            .map { SyncSetting(key = it.key, value = it.value) }

        return SyncSnapshot(
            protocolVersion = SyncSnapshot.PROTOCOL_VERSION,
            deviceName = deviceNameProvider.get(),
            exportedAt = System.currentTimeMillis(),
            profiles = profiles.map { it.toSync() },
            subjects = subjects,
            exams = exams,
            examGoals = goals,
            scores = scores,
            papers = papers,
            questions = questions,
            analysisReports = reports,
            settings = settings
        )
    }

    companion object {
        /**
         * 不跟着数据走的键。
         *
         * - `current_profile_id`：值是**本机**数据库里的行 id，搬到另一台设备
         *   上指向的可能是别的档案，同步过去只会制造错乱。导入后由
         *   ProfileSession 自己重新解析。
         * - `first_launch`：是不是第一次启动，每台设备各自算，不该被别人的
         *   快照改掉。
         */
        private val LOCAL_ONLY_KEYS = setOf(
            AppSettingRepository.KEY_CURRENT_PROFILE_ID,
            AppSettingRepository.KEY_FIRST_LAUNCH
        )
    }
}

// ---------------------------------------------------------------------------
// Entity → DTO。一行一个字段，不玩花活 —— 两边字段名不一致时（比如
// subject 的 sort_order），这里是唯一需要看一眼的地方。
// ---------------------------------------------------------------------------

private fun ProfileEntity.toSync() = SyncProfile(
    id = id,
    name = name,
    isDefault = isDefault,
    className = className,
    gradeName = gradeName,
    remark = remark,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun SubjectEntity.toSync() = SyncSubject(
    id = id,
    profileId = profileId,
    name = name,
    fullScore = fullScore,
    color = color,
    sortOrder = sortOrder
)

private fun ExamEntity.toSync() = SyncExam(
    id = id,
    profileId = profileId,
    name = name,
    type = type,
    date = date,
    remark = remark,
    updatedAt = updatedAt
)

private fun ExamGoalEntity.toSync() = SyncExamGoal(
    id = id,
    profileId = profileId,
    subjectId = subjectId,
    targetScore = targetScore,
    targetRank = targetRank,
    updatedAt = updatedAt
)

private fun ScoreEntity.toSync() = SyncScore(
    id = id,
    examId = examId,
    subjectId = subjectId,
    score = score,
    fullScore = fullScore,
    classAvg = classAvg,
    gradeAvg = gradeAvg,
    classRank = classRank,
    gradeRank = gradeRank,
    classTotal = classTotal,
    gradeTotal = gradeTotal,
    updatedAt = updatedAt
)

private fun PaperEntity.toSync() = SyncPaper(
    id = id,
    examId = examId,
    subjectId = subjectId,
    title = title,
    totalScore = totalScore,
    sourceType = sourceType,
    updatedAt = updatedAt
)

private fun QuestionEntity.toSync() = SyncQuestion(
    id = id,
    paperId = paperId,
    number = number,
    type = type,
    fullScore = fullScore,
    score = score,
    difficulty = difficulty,
    content = content,
    answer = answer,
    userAnswer = userAnswer,
    isCorrect = isCorrect,
    wrongReason = wrongReason,
    updatedAt = updatedAt
)

private fun AnalysisReportEntity.toSync() = SyncAnalysisReport(
    id = id,
    profileId = profileId,
    examId = examId,
    type = type,
    content = content,
    createdAt = createdAt
)