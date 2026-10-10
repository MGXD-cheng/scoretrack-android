package com.lingxi.scoretrack.data.file

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 极简 .xlsx 解析器。
 *
 * xlsx 本质就是一个 zip，里面两份 XML 决定了全部内容：
 * - `xl/sharedStrings.xml` —— 所有字符串集中存这儿
 * - `xl/worksheets/sheet1.xml` —— 单元格；数字直接写，字符串写的是 sharedStrings 的下标
 *
 * 所以只要解这两份 XML 就够了 —— **不用引 Apache POI（光体积就 10MB+）**。
 *
 * 不支持 .xls（BIFF 二进制老格式，那是另一套东西），遇到会提示用户另存为 xlsx。
 */
object XlsxParser {

    private const val MAX_ROWS = 200

    fun parse(input: InputStream): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        var sheetXml: ByteArray? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" ->
                        sharedStrings.addAll(parseSharedStrings(zip))

                    // 只取第一张表：成绩单一般就一张
                    sheetXml == null && name.startsWith("xl/worksheets/sheet") &&
                        name.endsWith(".xml") -> sheetXml = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }

        val xml = sheetXml ?: return emptyList()
        return parseSheet(xml.inputStream(), sharedStrings)
    }

    /** `<si><t>文本</t></si>`，富文本会拆成多个 `<t>`，拼起来即可 */
    private fun parseSharedStrings(input: InputStream): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        var current: StringBuilder? = null
        var insideText = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> insideText = true
                }

                XmlPullParser.TEXT -> if (insideText) current?.append(parser.text)

                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> insideText = false
                    "si" -> {
                        result.add(current?.toString().orEmpty())
                        current = null
                    }
                }
            }
            parser.next()
        }
        return result
    }

    private fun parseSheet(
        input: InputStream,
        sharedStrings: List<String>
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        var currentRow: MutableList<String>? = null
        var cellType: String? = null
        var cellValue: String? = null
        var insideValue = false
        var insideInlineText = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT && rows.size < MAX_ROWS) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        cellValue = null
                    }

                    "v" -> insideValue = true
                    "t" -> if (cellType == "inlineStr") insideInlineText = true
                }

                XmlPullParser.TEXT -> when {
                    insideValue -> cellValue = (cellValue ?: "") + parser.text
                    insideInlineText -> cellValue = (cellValue ?: "") + parser.text
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> insideValue = false
                    "t" -> insideInlineText = false

                    "c" -> {
                        val raw = cellValue.orEmpty()
                        val text = when (cellType) {
                            // t="s" 说明 v 里存的是 sharedStrings 下标
                            "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        currentRow?.add(text)
                        cellType = null
                        cellValue = null
                    }

                    "row" -> {
                        currentRow?.let { rows.add(it) }
                        currentRow = null
                    }
                }
            }
            parser.next()
        }

        return rows.filter { row -> row.any { it.isNotBlank() } }
    }
}