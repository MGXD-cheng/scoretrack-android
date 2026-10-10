package com.lingxi.scoretrack.data.file

/**
 * 极简 CSV 解析器。
 *
 * 为什么自己写：CSV 规范本身就很小，需求也只是「把一份成绩表读成行列」。
 * 而且真实成绩单里，逗号 / 分号 / Tab 都有人用，还得自动认。
 *
 * 支持：
 * - 引号包裹的字段（内部可以有分隔符、换行）
 * - 双引号转义（`""` → `"`）
 * - 分隔符自动探测
 * - 行数上限（防止一份几万行的表格把 prompt 撑爆）
 */
object CsvParser {

    /** 最多解析这么多行。成绩单不会超过，超了也是表格尾部无关内容 */
    private const val MAX_ROWS = 200

    private val CANDIDATE_DELIMITERS = listOf(',', ';', '\t', '|')

    /** 看第一行里哪个候选字符出现最多，就认为它是分隔符 */
    fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        return CANDIDATE_DELIMITERS
            .maxByOrNull { delimiter -> firstLine.count { it == delimiter } }
            ?.takeIf { delimiter -> firstLine.contains(delimiter) }
            ?: ','
    }

    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < text.length && rows.size < MAX_ROWS) {
            val ch = text[index]
            when {
                inQuotes -> when {
                    // 转义的引号："" → "
                    ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }

                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }

                ch == '"' -> inQuotes = true

                ch == delimiter -> {
                    currentRow.add(field.toString())
                    field.clear()
                }

                // 忽略 \r，统一等 \n
                ch == '\r' -> Unit

                ch == '\n' -> {
                    currentRow.add(field.toString())
                    field.clear()
                    rows.add(currentRow.toList())
                    currentRow.clear()
                }

                else -> field.append(ch)
            }
            index++
        }

        // 收尾：最后一行可能没有换行符
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow.toList())
        }

        return rows.filter { row -> row.any { it.isNotBlank() } }
    }
}