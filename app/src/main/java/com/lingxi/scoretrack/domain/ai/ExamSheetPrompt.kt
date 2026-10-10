package com.lingxi.scoretrack.domain.ai

import com.lingxi.scoretrack.domain.model.RawRecognizedExam
import com.lingxi.scoretrack.domain.model.RawRecognizedQuestion
import com.lingxi.scoretrack.domain.model.RawRecognizedSubject
import com.lingxi.scoretrack.domain.model.RecognizedExam
import com.lingxi.scoretrack.domain.model.RecognizedQuestion
import com.lingxi.scoretrack.domain.model.RecognizedSubject
import kotlinx.serialization.json.Json

/**
 * 成绩单识别的提示词与结果解析。
 *
 * 设计要点：
 * 1. **强制 JSON 输出**，并在提示里给出精确的字段名 —— 否则模型会自由发挥。
 * 2. **解析层极度宽容**：模型常返回 ```json 围栏、字符串数字、中文字段。
 *    解析失败时逐级降级，最后还能用正则兜底，尽量不让用户空手而归。
 */
object ExamSheetPrompt {

    val SYSTEM: String = """
        你是一个成绩单结构化助手。用户会给你一张成绩单 / 试卷 / 成绩查询页面的照片。

        请提取其中的考试信息与各科成绩，**只输出一个 JSON 对象**，不要输出任何解释文字，
        不要使用 markdown 代码块。

        JSON 结构必须严格如下：
        {
          "examName": "考试名称，如 高二上期中考试",
          "examDate": "yyyy-MM-dd，识别不到则 null",
          "subjects": [
            {
              "subjectName": "科目名，如 数学",
              "score": 128,
              "fullScore": 150,
              "questions": [
                {
                  "number": "题号，如 18",
                  "content": "题干（若能看清）",
                  "userAnswer": "学生的作答",
                  "correctAnswer": "正确答案",
                  "wrongReason": "错因（若图片中有批注）",
                  "score": 4,
                  "fullScore": 12,
                  "difficulty": "易/中/难（识别不到则 null）"
                }
              ]
            }
          ]
        }

        注意事项：
        - score / fullScore 必须是数字，不要带单位。
        - 满分识别不到时：语文/数学/英语按 150，其他科目按 100 估计。
        - questions 只收录**做错的题**；如果图片里没有逐题信息，questions 返回空数组。
        - 看不清的内容一律填 null，**不要编造**。
    """.trimIndent()

    const val USER = "请识别这张成绩单，按约定输出 JSON。"

    /**
     * 文本通道的系统提示词。
     *
     * 和图片版的关键差别：表格是**结构化但不规范**的 ——
     * 列名叫什么的都有（科目/学科/课程、成绩/得分/分数、满分/总分），
     * 所以这里明确要求「靠语义判断，不要死抠字面」。
     */
    val TEXT_SYSTEM: String = """
        你是一个成绩单结构化助手。用户会给你一份成绩单的**文本内容**
        （通常来自 CSV 或 Excel 导出）。

        内容格式：每行一条记录，列之间用 `|` 分隔，第一行通常是表头。

        请整理成 JSON，**只输出一个 JSON 对象**，不要解释文字，不要 markdown 代码块。

        JSON 结构必须严格如下：
        {
          "examName": "考试名称；若文本里没有，根据表头或上下文合理命名，如 成绩表导入",
          "examDate": "yyyy-MM-dd，识别不到则 null",
          "subjects": [
            {
              "subjectName": "科目名",
              "score": 128,
              "fullScore": 150,
              "questions": []
            }
          ]
        }

        注意事项：
        - **靠语义判断列的含义**，不要死抠列名字面。
          例如「科目/学科/课程/名称」都可能是科目名，
          「成绩/得分/分数/分值」都可能是得分。
        - score / fullScore 必须是数字，去掉单位。
        - 满分识别不到时：语文/数学/英语按 150，其他科目按 100 估计。
        - 文本成绩表通常没有逐题信息，questions 一律给空数组。
        - 跳过表头行、合计行、空行，**不要把表头当成一个科目**。
        - 看不清或没有的内容填 null，**不要编造**。
    """.trimIndent()

    val TEXT_USER = "请识别下面这份成绩数据，按约定输出 JSON：\n\n"
}

object RecognizedExamParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 解析模型输出。依次尝试：
     * 1. 直接当 JSON 解析
     * 2. 剥掉 ```json 围栏后再解析
     * 3. 截取第一个 `{` 到最后一个 `}` 之间的内容再解析
     */
    fun parse(raw: String): RecognizedExam? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null

        val candidates = buildList {
            add(cleaned)
            add(stripCodeFence(cleaned))
            add(sliceJsonObject(cleaned))
        }.filter { it.isNotBlank() }

        for (candidate in candidates) {
            val parsed = runCatching {
                json.decodeFromString(RawRecognizedExam.serializer(), candidate)
            }.getOrNull()
            if (parsed != null) {
                val mapped = parsed.toDomain()
                if (mapped != null) return mapped
            }
        }
        return null
    }

    private fun stripCodeFence(text: String): String =
        text.removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

    private fun sliceJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun RawRecognizedExam.toDomain(): RecognizedExam? {
        val mappedSubjects = subjects.mapNotNull { it.toDomain() }
        if (mappedSubjects.isEmpty()) return null

        return RecognizedExam(
            examName = examName?.takeIf { it.isNotBlank() } ?: "未命名考试",
            examDate = normalizeDate(examDate),
            subjects = mappedSubjects
        )
    }

    private fun RawRecognizedSubject.toDomain(): RecognizedSubject? {
        val subject = (subjectName ?: name)?.trim().orEmpty()
        if (subject.isEmpty()) return null

        val parsedScore = score?.toFloatOrNull() ?: return null
        val parsedFull = fullScore?.toFloatOrNull() ?: defaultFullScore(subject)

        return RecognizedSubject(
            subjectName = subject,
            score = parsedScore,
            fullScore = if (parsedFull > 0f) parsedFull else defaultFullScore(subject),
            questions = questions.mapNotNull { it.toDomain() }
        )
    }

    private fun RawRecognizedQuestion.toDomain(): RecognizedQuestion? {
        val questionNumber = number?.takeIf { it.isNotBlank() } ?: return null
        // 整题都没信息就丢掉，避免塞一堆空壳进错题本
        val hasAnyContent = listOf(content, userAnswer, correctAnswer, wrongReason)
            .any { !it.isNullOrBlank() }
        if (!hasAnyContent) return null

        return RecognizedQuestion(
            number = questionNumber,
            content = content?.trim(),
            userAnswer = userAnswer?.trim(),
            correctAnswer = correctAnswer?.trim(),
            wrongReason = wrongReason?.trim(),
            score = score?.toFloatOrNull(),
            fullScore = fullScore?.toFloatOrNull(),
            difficulty = difficulty?.takeIf { it.isNotBlank() }
        )
    }

    /** 语数英默认 150，其余 100 */
    private fun defaultFullScore(subject: String): Float =
        if (subject.contains("语") || subject.contains("数") || subject.contains("英")) 150f else 100f

    /** 兼容 2024/5/1、2024年5月1日 等写法，统一成 yyyy-MM-dd */
    private fun normalizeDate(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null

        val digits = Regex("(\\d{4})\\D(\\d{1,2})\\D(\\d{1,2})").find(text) ?: return null
        val (year, month, day) = digits.destructured
        return "%s-%02d-%02d".format(year, month.toInt(), day.toInt())
    }
}