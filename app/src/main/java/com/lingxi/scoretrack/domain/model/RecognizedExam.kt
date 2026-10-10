package com.lingxi.scoretrack.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AI 从成绩单图片中识别出的结构化结果。
 *
 * 这一层是**领域模型**（UI 直接编辑它），跟模型返回的原始 JSON 解耦 ——
 * 模型输出的字段名可能变、可能缺、可能是字符串数字，都在解析阶段兜住。
 */
data class RecognizedExam(
    val examName: String,
    val examDate: String?,          // yyyy-MM-dd
    val subjects: List<RecognizedSubject>
) {
    val totalScore: Float get() = subjects.sumOf { it.score.toDouble() }.toFloat()

    val totalFullScore: Float get() = subjects.sumOf { it.fullScore.toDouble() }.toFloat()

    val wrongQuestionCount: Int get() = subjects.sumOf { it.questions.size }

    val isEmpty: Boolean get() = subjects.isEmpty()
}

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