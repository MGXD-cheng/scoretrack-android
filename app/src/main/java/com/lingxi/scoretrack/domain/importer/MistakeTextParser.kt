package com.lingxi.scoretrack.domain.importer

/**
 * 一行解析结果（还没落库）。
 */
data class ParsedMistake(
    val subjectName: String,
    val type: String,
    val content: String,
    val answer: String,
    val userAnswer: String,
    val wrongReason: String
)

data class ParseOutcome(
    val rows: List<ParsedMistake>,
    /** 行号 + 原因，直接展示给用户 */
    val errors: List<String>
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** 按题型计数，用于导入前的预览："选择题 3 道、填空题 2 道" */
    val countByType: Map<String, Int>
        get() = rows.groupingBy { it.type }.eachCount()
}

/**
 * 错题文本导入的解析器。
 *
 * ## 格式
 *
 * 一行一道题，**半角逗号**分隔，六列：
 *
 * ```
 * 科目,题型,题目,正确答案,我的答案,错因
 * ```
 *
 * 后面几列可以留空。以 `#` 开头的行和空行会被忽略，所以示例里可以带注释。
 *
 * ## 为什么用半角逗号而不是别的分隔符
 *
 * 中文用户打字时说的是「逗号」，打出来的是全角 `，`。题目正文里的逗号
 * 几乎必然是全角 —— 于是半角逗号天然就是一个不会撞车的分隔符。
 * 真遇到正文里必须写半角逗号的情况，用双引号把整列包起来即可（CSV 惯例）。
 */
object MistakeTextParser {

    /** 六列的语义，用在说明和示例里 */
    const val COLUMNS = "科目, 题型, 题目, 正确答案, 我的答案, 错因"

    /**
     * 示例。
     *
     * 覆盖三种题型，其中选择题是这次新加的分类 —— 之前导入功能压根没有题型概念。
     * 每行都是可以直接导入的真实内容，不是占位符，用户点「插入示例」之后
     * 立刻能看出导入结果长什么样。
     */
    val SAMPLE = """
        # 一行一道题，用半角逗号分隔：科目, 题型, 题目, 正确答案, 我的答案, 错因
        # 题型写 选择题 / 填空题 / 解答题；最后两列可以留空
        数学,选择题,若 f(x)=x²+2x，则 f(1) 的值是？ A.1 B.2 C.3 D.4,C,A,把 x=1 代成了 x=-1
        数学,选择题,下列函数中是奇函数的是？ A.y=x² B.y=x³ C.y=|x| D.y=x+1,B,D,奇偶性定义记混了
        数学,填空题,sin30° = ____,1/2,√3/2,特殊角的三角函数值记混
        英语,选择题,He ____ to school every day. A.go B.goes C.going D.gone,B,A,主谓一致：第三人称单数
        物理,解答题,质量 2kg 的物体受 10N 水平推力，动摩擦因数 0.2，求加速度,3 m/s²,2 m/s²,忘记减去摩擦力
    """.trimIndent()

    fun parse(raw: String): ParseOutcome {
        val rows = mutableListOf<ParsedMistake>()
        val errors = mutableListOf<String>()

        raw.lineSequence().forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed

            val cells = splitCsv(trimmed)
            // 至少要有「科目 + 题目」，只有科目的行没有意义
            if (cells.size < 3) {
                errors += "第 $lineNumber 行：列数不够，至少要「科目,题型,题目」"
                return@forEachIndexed
            }

            val subject = cells.getOrNull(0).orEmpty().trim()
            val type = normalizeType(cells.getOrNull(1).orEmpty())
            val content = cells.getOrNull(2).orEmpty().trim()

            if (subject.isEmpty()) {
                errors += "第 $lineNumber 行：科目是空的"
                return@forEachIndexed
            }
            if (content.isEmpty()) {
                errors += "第 $lineNumber 行：题目内容是空的"
                return@forEachIndexed
            }

            rows += ParsedMistake(
                subjectName = subject,
                type = type,
                content = content,
                answer = cells.getOrNull(3).orEmpty().trim(),
                userAnswer = cells.getOrNull(4).orEmpty().trim(),
                wrongReason = cells.getOrNull(5).orEmpty().trim()
            )
        }

        return ParseOutcome(rows = rows, errors = errors)
    }

    /**
     * 题型归一化。
     *
     * 用户可能写「选择」「选择题」「单选题」「单选」，都归到选择题 ——
     * 让他们自由写、系统来对齐，比报错要求精确用词友好得多。
     * 认不出来的保留原文，总比丢掉强。
     */
    fun normalizeType(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "选择题"
        return when {
            value.contains("选择") || value.contains("单选") || value.contains("多选") -> "选择题"
            value.contains("填空") -> "填空题"
            value.contains("解答") || value.contains("计算") || value.contains("证明") -> "解答题"
            else -> value
        }
    }

    /**
     * 按半角逗号切分，支持双引号包裹（CSV 惯例：`"a,b",c` → [a,b] [c]）。
     *
     * 不追求完整的 RFC 4180（不支持引号内换行 —— 那本来也不该出现在单行格式里）。
     */
    private fun splitCsv(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' -> {
                    // 连续两个引号表示一个字面量引号
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }

                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }

                else -> current.append(ch)
            }
            index++
        }
        result += current.toString()
        return result
    }
}