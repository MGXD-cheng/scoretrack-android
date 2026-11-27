package com.lingxi.scoretrack.domain.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 局域网同步的传输协议。
 *
 * ## 为什么不用 Entity 直接序列化
 * Entity 上带着 Room 的注解，序列化它等于把「数据库结构」和「网络协议」
 * 焊死：改一个列名就得同时考虑另一边。所以这里全部用独立的 DTO ——
 * 协议可以自己演化，老版本 App 收到新快照也不会因为多个字段就崩
 * （全靠 [SyncSnapshot.protocolVersion] + 解析时的 ignoreUnknownKeys）。
 *
 * ## 关于 id
 * DTO **保留源端的原始 id**。这些 id 只在这个快照内部有意义 ——
 * 导入时会被整表重映射成目标端的新 id，两张表之间的关联靠它们对上。
 * 所以绝对不要拿快照里的 id 直接往本地插。
 *
 * ## 字段默认值
 * 所有字段都给了默认值。少一个字段（老版本导出的快照）不会解析失败，
 * 只是那一项为空。
 */
@Serializable
data class SyncSnapshot(
    /** 协议版本。将来做不兼容变更时靠它拒绝，而不是解析出一堆垃圾 */
    @SerialName("protocolVersion") val protocolVersion: Int = PROTOCOL_VERSION,
    @SerialName("deviceName") val deviceName: String = "",
    @SerialName("exportedAt") val exportedAt: Long = 0L,
    @SerialName("profiles") val profiles: List<SyncProfile> = emptyList(),
    @SerialName("subjects") val subjects: List<SyncSubject> = emptyList(),
    @SerialName("exams") val exams: List<SyncExam> = emptyList(),
    @SerialName("examGoals") val examGoals: List<SyncExamGoal> = emptyList(),
    @SerialName("scores") val scores: List<SyncScore> = emptyList(),
    @SerialName("papers") val papers: List<SyncPaper> = emptyList(),
    @SerialName("questions") val questions: List<SyncQuestion> = emptyList(),
    @SerialName("analysisReports") val analysisReports: List<SyncAnalysisReport> = emptyList(),

    /**
     * 用户偏好设置（键值对）。
     *
     * 加这一项的起因很具体：自定义考试类型存在 `app_setting` 里，而之前的
     * 快照根本没带这张表 —— 换手机之后自己加的类型全丢，只剩 7 个默认值。
     *
     * **API Key 不在这里**。它存在 EncryptedSharedPreferences，从来没进过
     * 数据库，所以备份文件里也不会有 —— 备份文件可能被拷进网盘、通过局域网
     * 明文传输，把密钥放进来的想法从第一步就不该有。
     */
    @SerialName("settings") val settings: List<SyncSetting> = emptyList()
) {
    /** 概览用：总共多少条记录 */
    val totalRecords: Int
        get() = profiles.size + subjects.size + exams.size + examGoals.size +
            scores.size + papers.size + questions.size +
            analysisReports.size + settings.size

    companion object {
        const val PROTOCOL_VERSION = 1
    }
}

@Serializable
data class SyncProfile(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("className") val className: String? = null,
    @SerialName("gradeName") val gradeName: String? = null,
    @SerialName("remark") val remark: String? = null,
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L
)

@Serializable
data class SyncSubject(
    @SerialName("id") val id: Long,
    @SerialName("profileId") val profileId: Long,
    @SerialName("name") val name: String,
    @SerialName("fullScore") val fullScore: Float = 100f,
    @SerialName("color") val color: String = "#2A6DF4",
    @SerialName("sortOrder") val sortOrder: Int = 0
)

@Serializable
data class SyncExam(
    @SerialName("id") val id: Long,
    @SerialName("profileId") val profileId: Long,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String = "常规考试",
    @SerialName("date") val date: Long = 0L,
    @SerialName("remark") val remark: String? = null,
    @SerialName("updatedAt") val updatedAt: Long = 0L
)

@Serializable
data class SyncExamGoal(
    @SerialName("id") val id: Long,
    @SerialName("profileId") val profileId: Long,
    @SerialName("subjectId") val subjectId: Long? = null,
    @SerialName("targetScore") val targetScore: Float = 0f,
    @SerialName("targetRank") val targetRank: Int? = null,
    @SerialName("updatedAt") val updatedAt: Long = 0L
)

@Serializable
data class SyncScore(
    @SerialName("id") val id: Long,
    @SerialName("examId") val examId: Long,
    @SerialName("subjectId") val subjectId: Long,
    @SerialName("score") val score: Float = 0f,
    @SerialName("fullScore") val fullScore: Float = 100f,
    @SerialName("classAvg") val classAvg: Float? = null,
    @SerialName("gradeAvg") val gradeAvg: Float? = null,
    @SerialName("classRank") val classRank: Int? = null,
    @SerialName("gradeRank") val gradeRank: Int? = null,
    @SerialName("classTotal") val classTotal: Int? = null,
    @SerialName("gradeTotal") val gradeTotal: Int? = null,
    @SerialName("updatedAt") val updatedAt: Long = 0L
)

@Serializable
data class SyncPaper(
    @SerialName("id") val id: Long,
    @SerialName("examId") val examId: Long,
    @SerialName("subjectId") val subjectId: Long,
    @SerialName("title") val title: String = "",
    @SerialName("totalScore") val totalScore: Float = 0f,
    @SerialName("sourceType") val sourceType: String = "manual",
    /**
     * 刻意**不传** sourceFileUrl —— 那是个本机 content:// 或
     * file:// 路径，换台设备根本指不到东西，传过去只会变成一条坏链接。
     */
    @SerialName("updatedAt") val updatedAt: Long = 0L
)

@Serializable
data class SyncQuestion(
    @SerialName("id") val id: Long,
    @SerialName("paperId") val paperId: Long,
    @SerialName("number") val number: String = "",
    @SerialName("type") val type: String? = null,
    @SerialName("fullScore") val fullScore: Float = 0f,
    @SerialName("score") val score: Float = 0f,
    @SerialName("difficulty") val difficulty: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("answer") val answer: String? = null,
    @SerialName("userAnswer") val userAnswer: String? = null,
    @SerialName("isCorrect") val isCorrect: Boolean? = null,
    @SerialName("wrongReason") val wrongReason: String? = null,
    @SerialName("updatedAt") val updatedAt: Long = 0L
)

@Serializable
data class SyncAnalysisReport(
    @SerialName("id") val id: Long,
    @SerialName("profileId") val profileId: Long,
    @SerialName("examId") val examId: Long? = null,
    @SerialName("type") val type: String = "综合",
    @SerialName("content") val content: String = "",
    @SerialName("createdAt") val createdAt: Long = 0L
)

/**
 * 一条用户偏好设置。
 *
 * 没有 id、没有外键 —— 键名自带唯一性。同步时的合并规则在
 * [SnapshotImporter] 里，按 key 分两类处理：累积型的（自定义考试类型）
 * 取并集，开关型的（自动归纳）直接以传入的快照为准。
 */
@Serializable
data class SyncSetting(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String = ""
)