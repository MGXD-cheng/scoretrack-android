package com.lingxi.scoretrack.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lingxi.scoretrack.util.ImageEncoder

/**
 * 文件 →「可交给 AI 的东西」。
 *
 * 只有两个出口，对应两条已有的通道：
 * - [Extracted.Text]   —— CSV / XLSX / TXT → 文本模型（快、便宜、准）
 * - [Extracted.Images] —— PDF / 图片      → 视觉模型（上一轮已跑通）
 *
 * 提取失败时抛的异常 message **会直接展示给用户**，所以写人话。
 */
object DocumentExtractor {

    sealed interface Extracted {
        data class Text(val content: String, val fileType: String) : Extracted
        data class Images(val dataUrls: List<String>, val fileType: String) : Extracted
    }

    fun extract(context: Context, uri: Uri): Extracted {
        val name = queryDisplayName(context, uri)
        val extension = name.substringAfterLast('.', "").lowercase()
        val mimeType = context.contentResolver.getType(uri).orEmpty()

        return when (extension) {
            "csv", "txt" -> {
                val raw = readTextAsBestEffort(context, uri)
                    ?: error("读不出文件内容，换个文件试试")
                Extracted.Text(
                    content = renderTable(CsvParser.parse(raw)),
                    fileType = extension
                )
            }

            "xlsx" -> {
                val rows = context.contentResolver.openInputStream(uri)
                    ?.use { XlsxParser.parse(it) }
                    ?: error("打不开这个 Excel 文件")
                if (rows.isEmpty()) error("表格里没读到内容，确认一下不是空表")
                Extracted.Text(content = renderTable(rows), fileType = "xlsx")
            }

            "pdf" -> {
                val urls = PdfPageRenderer.toDataUrls(context, uri)
                if (urls.isEmpty()) error("这个 PDF 渲染失败，可能是加密或损坏的文件")
                Extracted.Images(urls, fileType = "pdf")
            }

            "xls" -> error(".xls 是老格式读不了，请在 Excel 里另存为 .xlsx 或 .csv")

            else -> {
                if (mimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS) {
                    // 先确认文件不是空的 —— 厂商相机偶尔会返回「拍成功」但什么也没写
                    val size = querySize(context, uri)
                    if (size == 0L) {
                        error("这张照片是空的（0 字节）—— 相机可能没写成功，重新拍一张试试")
                    }
                    val url = ImageEncoder.toDataUrl(context, uri)
                        // 自己解不开，不代表模型也读不了 —— 原样发过去试一次
                        ?: ImageEncoder.toRawDataUrl(context, uri, mimeType)
                        ?: error(
                            "这张图片读不出来（${size / 1024} KB，$mimeType）—— " +
                                "换一张，或者用系统相册先转成 PNG/JPG"
                        )
                    Extracted.Images(listOf(url), fileType = "image")
                } else {
                    error("暂不支持 .$extension 这种格式，试试 CSV / Excel / PDF / 图片")
                }
            }
        }
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic")

    /**
     * 文件选择器的 MIME 过滤。
     *
     * 放在这里而不是散落在各个页面 —— 以后加一种格式只用改一处。
     * （注意：本文件其他地方也别在注释里写出通配符的原始写法，
     * 那串字符会开启 Kotlin 的嵌套注释，把整个文件吞掉。）
     */
    val SUPPORTED_MIME_TYPES = arrayOf(
        "text/csv",
        "text/comma-separated-values",
        "text/plain",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "application/pdf",
        "image/*"
    )

    /** 用 `|` 分隔列。比对齐的表格式更省 token，模型照样读得懂 */
    private fun renderTable(rows: List<List<String>>): String =
        rows.joinToString("\n") { row -> row.joinToString(" | ") { it.trim() } }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index).orEmpty()
                }
            }
        return uri.lastPathSegment.orEmpty()
    }

    /** 读文件大小。查不到时返回 -1，不要返回 0 —— 那会被误判成空文件 */
    private fun querySize(context: Context, uri: Uri): Long {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        return -1L
    }

    /**
     * 读纯文本文件。
     *
     * **中文 CSV 用 GBK 保存的非常多**（尤其 Excel 直接导出的），
     * 硬按 UTF-8 解会满屏乱码。这里先试 UTF-8，
     * 如果替换字符（U+FFFD）占比超过 5%，就回退到 GBK 再解一次。
     */
    private fun readTextAsBestEffort(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (bytes.isEmpty()) return null

        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0

        val utf8 = String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
        val replacementRatio = utf8.count { it == '\uFFFD' }.toFloat() / utf8.length.coerceAtLeast(1)

        return if (replacementRatio > 0.05f) {
            runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
        } else {
            utf8
        }
    }
}