package com.lingxi.scoretrack.domain.ai

import com.lingxi.scoretrack.data.local.model.MistakeDetailRow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「从错题归纳知识点」的提示词与解析器。
 *
 * 设计要点：**一次调用同时完成两件事** —— 归纳知识点清单 + 给每道题打标签。
 * 分开调两次不仅要多花一倍 token，而且模型看不到全局，
 * 归纳出的知识点和归类结果容易对不上。
 */
object KnowledgePointPrompt {

    const val SYSTEM = """你是一位经验丰富的中学教师，擅长从学生的错题中归纳知识薄弱点。

请阅读我给出的错题列表，完成两件事：
1. 归纳出这些错题涉及的【知识点】；
2. 给每道题标注它涉及的知识点。

要求：
- 知识点名称要具体、可用，用教材或考纲里的标准表述。
  例如用「二次函数图象的对称轴」，不要用「数学思维」「综合能力」这类空泛的词。
- 同一知识点在多道题中出现时只列一次，不要重复。
- 一道题可以对应多个知识点；一道题也可以暂时归纳不出知识点，那就不要给它标。
- 每道题涉及的知识点，必须来自你归纳出的知识点名称，不要另造名字。
- 知识点名称后的 subject 字段，要填这道题所属的科目。
- 只输出 JSON，不要任何解释文字，不要用 Markdown 代码块包裹。

输出格式：
{"points":[{"name":"知识点名称","subject":"科目名","aliases":["常见别名"]}],"questions":[{"index":1,"points":["知识点名称"]}]}"""

    /**
     * 组装错题列表。
     *
     * 每题带一个从 1 开始的**序号** —— 让模型引用序号，
     * 比让它引用 questionId 靠谱得多（长数字很容易抄错）。
     */
    fun buildUserContent(
        rows: List<MistakeDetailRow>,
        subjectNames: Map<Long, String>
    ): String = buildString {
        appendLine("以下是 ${rows.size} 道错题：")
        appendLine()
        rows.forEachIndexed { index, row ->
            val subject = subjectNames[row.subjectId] ?: "未知科目"
            appendLine("第 ${index + 1} 题【$subject】")
            appendLine("  题干：${row.content?.takeIf { it.isNotBlank() } ?: "（未记录）"}")
            appendLine("  我的答案：${row.userAnswer?.takeIf { it.isNotBlank() } ?: "（未记录）"}")
            appendLine("  正确答案：${row.answer?.takeIf { it.isNotBlank() } ?: "（未记录）"}")
            row.wrongReason?.takeIf { it.isNotBlank() }
                ?.let { appendLine("  错因：$it") }
            appendLine()
        }
        appendLine("请按约定输出 JSON。")
    }

    data class ParsedPoint(
        val name: String,
        val subjectName: String,
        val aliases: List<String>
    )

    /** assignments：题目序号（1 起）→ 知识点名称列表 */
    data class ParsedResult(
        val points: List<ParsedPoint>,
        val assignments: Map<Int, List<String>>
    )

    /** @return null 表示这份回复根本不像我们要的 JSON */
    fun parse(raw: String): ParsedResult? {
        val json = extractJson(raw) ?: return null
        return runCatching {
            val root = JSONObject(json)

            val pointArray = root.optJSONArray("points") ?: JSONArray()
            val points = buildList {
                for (i in 0 until pointArray.length()) {
                    val item = pointArray.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isEmpty()) continue
                    add(
                        ParsedPoint(
                            name = name,
                            subjectName = item.optString("subject").trim(),
                            aliases = item.optJSONArray("aliases").toStringList()
                        )
                    )
                }
            }

            val questionArray = root.optJSONArray("questions") ?: JSONArray()
            val assignments = buildMap {
                for (i in 0 until questionArray.length()) {
                    val item = questionArray.optJSONObject(i) ?: continue
                    val index = item.optInt("index", -1)
                    if (index < 0) continue
                    val names = item.optJSONArray("points").toStringList()
                    if (names.isNotEmpty()) put(index, names)
                }
            }

            if (points.isEmpty()) null else ParsedResult(points, assignments)
        }.getOrNull()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    /**
     * 三级降级提取，和成绩单解析器保持同一套策略：
     * 直接解析 → 剥掉 Markdown 围栏 → 截取首个大括号到末个大括号。
     */
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