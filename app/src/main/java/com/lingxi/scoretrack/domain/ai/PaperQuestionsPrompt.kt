package com.lingxi.scoretrack.domain.ai

import com.lingxi.scoretrack.domain.model.QuestionDraft
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「从文件里提取小题分」的提示词与解析器。
 *
 * 和识别整张成绩单不同：这里要的是**逐题的得分明细**，
 * 所以模型必须把每一道小题列出来，而不是给各科总分。
 */
object PaperQuestionsPrompt {

    const val SYSTEM = """你是一位细心的老师，正在帮学生把一张试卷的得分明细录入系统。

请从我给出的材料里，把**每一道小题**提取成一条记录。

要求：
- number 填题号，用材料里原本的写法（如 "1"、"12"、"三(2)"）。
- fullScore 是该题满分，score 是学生实际得分，都用数字。
- 如果材料里只有各科总分、没有小题明细，就不要编造，返回空的 questions 数组。
- content 填题干或题目的简要描述，没写就留空字符串。
- answer 是正确答案，userAnswer 是学生的答案，分不清就留空字符串。
- difficulty 只能填 "易"、"中"、"难" 三者之一，判断不了就留空字符串。
- wrongReason 填错因，没写就留空字符串。
- 只输出 JSON，不要任何解释文字，不要用 Markdown 代码块包裹。

输出格式：
{"questions":[{"number":"1","fullScore":10,"score":8,"content":"","answer":"","userAnswer":"","difficulty":"中","wrongReason":""}]}"""

    const val USER = "请提取这张试卷的每一道小题，按约定输出 JSON。"

    /**
     * @return null 表示这份回复不像我们要的 JSON；
     *         空列表表示「确实没有小题明细」，这两者必须区分开。
     */
    fun parse(raw: String): List<QuestionDraft>? {
        val json = extractJson(raw) ?: return null
        return runCatching {
            val root = JSONObject(json)
            val array = root.optJSONArray("questions") ?: JSONArray()

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val number = item.optString("number").trim()
                    val fullScore = item.optDoubleOrNull("fullScore")
                    val score = item.optDoubleOrNull("score")

                    // 一道题至少得有题号，或者有分数 —— 什么都没有的就是噪声
                    if (number.isEmpty() && fullScore == null && score == null) continue

                    add(
                        QuestionDraft(
                            number = number.ifEmpty { (size + 1).toString() },
                            fullScore = fullScore?.let(::plain) ?: "",
                            score = score?.let(::plain) ?: "",
                            difficulty = item.optString("difficulty")
                                .trim()
                                .takeIf { it in DIFFICULTY_VALUES },
                            content = item.optString("content").trim(),
                            answer = item.optString("answer").trim(),
                            userAnswer = item.optString("userAnswer").trim(),
                            wrongReason = item.optString("wrongReason").trim()
                        )
                    )
                }
            }
        }.getOrNull()
    }

    private val DIFFICULTY_VALUES = setOf("易", "中", "难")

    /** 整数就不带小数点，`8.0` 显示成 `8` 更好看，也少占输入框空间 */
    private fun plain(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { !it.isNaN() }
    }

    /** 和成绩单解析器同一套三级降级策略 */
    private fun extractJson(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed

        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
            .find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        if (fenced != null && fenced.startsWith("{")) return fenced

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
    }
}