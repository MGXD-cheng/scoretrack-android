package com.lingxi.scoretrack.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AI 从成绩单图片中识别出的结构化结果。
 *
 * 这一层是**领域模型**（UI 直接编辑它），跟模型返回的原始 JSON 解耦 ——
 * 模型输出的字段名可能变、可能缺、可能是字符串数字，都在解析阶段兜住。
 *
 * ## 关于 [detectedStudents]
 * 一份文件里可能有多个人的成绩（班级成绩表就是这么干的）。
 * [subjects] 永远表示**当前选中的那个人**的成绩 —— 这样确认页、落库
 * 那些现有代码完全不用感知「多人」这件事。候选名单另外挂在
 * [detectedStudents] 上，只有真的多于一个人时才有值。
 */
data class RecognizedExam(
    val examName: String,
    val examDate: String?,          // yyyy-MM-dd
    val subjects: List<RecognizedSubject>,
    /**
     * 这份文件里识别出的**全部**学生。
     *
     * 只识别出一个人（绝大多数情况）时为空列表 —— UI 据此判断
     * 「要不要弹选择器」，不用再去比长度。
     */
    val detectedStudents: List<DetectedStudent> = emptyList()
) {
    val totalScore: Float get() = subjects.sumOf { it.score.toDouble() }.toFloat()

    val totalFullScore: Float get() = subjects.sumOf { it.fullScore.toDouble() }.toFloat()

    val wrongQuestionCount: Int get() = subjects.sumOf { it.questions.size }

    val isEmpty: Boolean get() = subjects.isEmpty()
}

/**
 * 一份文件里识别出的某个学生。
 *
 * [name] 恒非空 —— 模型没给出名字的会占位成「学生 N」，
 * 而不是留个空字符串让 UI 显示成空白选项。
 */
data class DetectedStudent(
    val name: String,
    val subjects: List<RecognizedSubject>
)

data class RecognizedSubject(
    val subjectName: String,
    val score: Float,
    val fullScore: Float,
    /** 该科目下被识别为做错的题目（可为空：只识别了总分） */
    val questions: List<RecognizedQuestion> = emptyList()
)

data class RecognizedQuestion(
    val number: String,
    val content: String? = null,
    val userAnswer: String? = null,
    val correctAnswer: String? = null,
    val wrongReason: String? = null,
    val score: Float? = null,
    val fullScore: Float? = null,
    val difficulty: String? = null
)

/**
 * 识别结果 + 模型原始返回。
 * 原始返回会被存进 import_task，方便出问题时排查是模型不准还是解析有 bug。
 */
data class RecognizeResult(
    val exam: RecognizedExam,
    val rawResponse: String
)

// ---------------------------------------------------------------------------
// 以下是「模型原始输出」的映射，只用于解析，不对外暴露。
// 全部字段给默认值 + 用 String 承接数字，最大限度容忍模型的不规范输出。
// ---------------------------------------------------------------------------

@Serializable
internal data class RawRecognizedExam(
    @SerialName("examName") val examName: String? = null,
    @SerialName("examDate") val examDate: String? = null,
    /** 单人格式（旧提示词 / 模型偷懒时走这条） */
    @SerialName("subjects") val subjects: List<RawRecognizedSubject> = emptyList(),
    /** 多人格式（新提示词）。两种都给默认值，解析层两种都认 */
    @SerialName("students") val students: List<RawRecognizedStudent> = emptyList()
)

@Serializable
internal data class RawRecognizedStudent(
    @SerialName("name") val name: String? = null,
    @SerialName("studentName") val studentName: String? = null,
    @SerialName("subjects") val subjects: List<RawRecognizedSubject> = emptyList()
)

@Serializable
internal data class RawRecognizedSubject(
    @SerialName("subjectName") val subjectName: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("score") val score: String? = null,
    @SerialName("fullScore") val fullScore: String? = null,
    @SerialName("questions") val questions: List<RawRecognizedQuestion> = emptyList()
)

@Serializable
internal data class RawRecognizedQuestion(
    @SerialName("number") val number: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("userAnswer") val userAnswer: String? = null,
    @SerialName("correctAnswer") val correctAnswer: String? = null,
    @SerialName("wrongReason") val wrongReason: String? = null,
    @SerialName("score") val score: String? = null,
    @SerialName("fullScore") val fullScore: String? = null,
    @SerialName("difficulty") val difficulty: String? = null
)