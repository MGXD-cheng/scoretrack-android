package com.lingxi.scoretrack.data.file

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 极简 .docx 解析器 —— 把 Word 正文抽成「文字 + 表格」的纯文本。
 *
 * 和 [XlsxParser] 是同一个套路：docx 也是个 zip，正文全在
 * `word/document.xml` 这一份 XML 里，所以**不用引 Apache POI**。
 *
 * ## 输出什么
 * - 段落 → 一行文字
 * - 表格 → 每行用 ` | ` 分隔，**和 [DocumentExtractor] 渲染 CSV/Excel 的格式
 *   完全一致** —— 下游喂给模型的东西长得一样，模型就不用为两种来源学两套读法。
 *
 * ## 刻意不做什么
 * 不还原字号、颜色、粗体、页眉页脚、批注 —— 这些对「提取成绩」是纯噪声，
 * 而且照样得塞进 prompt 白占 token。
 *
 * ## 已知边界
 * 如果文档内容是**一张图片**（扫描件粘进 Word 的常见做法），这里抽不出任何
 * 文字，会得到空串。调用方需要据此给出「请改用拍照识别」的提示，
 * 而不是安静地送一份空文本给模型。
 */
object DocxParser {

    /** 最多输出这么多行。成绩单不会超过，超了也是尾部无关内容 */
    private const val MAX_ROWS = 200

    private const val DOCUMENT_ENTRY = "word/document.xml"

    fun parse(input: InputStream): String {
        var documentXml: ByteArray? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == DOCUMENT_ENTRY) {
                    documentXml = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }

        // 找不到正文通常意味着这不是一个正经的 .docx（比如把 .doc 改了后缀）
        val xml = documentXml ?: return ""
        return parseDocument(xml.inputStream())
    }

    private fun parseDocument(input: InputStream): String {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val output = StringBuilder()

        /** 当前表格行要攒的单元格 */
        var rowCells: MutableList<String>? = null

        /** 当前单元格正在收集的文字；null 表示此刻不在单元格里 */
        var cell: StringBuilder? = null

        /** 当前段落正在收集的文字 */
        var paragraph: StringBuilder? = null

        var insideText = false
        var rowCount = 0

        while (parser.eventType != XmlPullParser.END_DOCUMENT && rowCount < MAX_ROWS) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.localName()) {
                    "tr" -> rowCells = mutableListOf()
                    "tc" -> cell = StringBuilder()
                    "p" -> paragraph = StringBuilder()
                    "t" -> insideText = true

                    // 表格里用制表符没意义（已经被 ` | ` 取代），但正文里的制表符
                    // 是有效的排版信息，保留
                    "tab" -> if (cell == null) paragraph?.append('\t')

                    // 硬换行：单元格里折成两行是常见写法，转成空格比丢掉强
                    "br" -> if (cell != null) cell.append(' ') else paragraph?.append('\n')
                }

                XmlPullParser.TEXT -> if (insideText) {
                    // 文字优先归给单元格；不在表格里才归给段落
                    val target = cell ?: paragraph
                    target?.append(parser.text)
                }

                XmlPullParser.END_TAG -> when (parser.localName()) {
                    "t" -> insideText = false

                    "tc" -> {
                        rowCells?.add(cell?.toString()?.trim().orEmpty())
                        cell = null
                    }

                    "tr" -> {
                        val cells = rowCells
                        if (cells != null && cells.any { it.isNotBlank() }) {
                            output.append(cells.joinToString(" | "))
                            output.append('\n')
                            rowCount++
                        }
                        rowCells = null
                    }

                    "p" -> {
                        // 表格里的段落不单独成行 —— 它的文字已经进单元格了
                        if (rowCells == null) {
                            val line = paragraph?.toString()?.trim().orEmpty()
                            if (line.isNotEmpty()) {
                                output.append(line)
                                output.append('\n')
                                rowCount++
                            }
                        }
                        paragraph = null
                    }
                }
            }
            parser.next()
        }

        return output.toString().trim()
    }

    /**
     * 取不带命名空间前缀的标签名。
     *
     * docx 用的是带前缀的写法（`w:p`、`w:t`），而 xlsx 那份 XML 不带前缀。
     * 与其在代码里写死 `w:`，不如一律取 local name —— 换个前缀也不会崩。
     */
    private fun XmlPullParser.localName(): String = name.substringAfterLast(':')
}