package com.lingxi.scoretrack.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 把 PDF 前几页渲染成图片，交给视觉模型去「看」。
 *
 * 为什么不做文本层提取：
 * - 带文字层的 PDF 可以用 PDFBox，但那是个几 MB 的依赖
 * - 而扫描件、手机拍的 PDF **根本没有文字层**
 *
 * 统一走「渲染成图 → 视觉识别」一条路，**覆盖面更广、依赖为零**，
 * 而且复用的正是上一轮已经跑通的图片通道。
 */
object PdfPageRenderer {

    /** 只渲染前几页：成绩单不会太长，也避免单次请求体积失控 */
    private const val MAX_PAGES = 2

    /** 约 A4 @150dpi，足够模型看清小字，又不会太占带宽 */
    private const val TARGET_WIDTH = 1240
    private const val JPEG_QUALITY = 80

    fun toDataUrls(context: Context, uri: Uri): List<String> {
        val descriptor: ParcelFileDescriptor =
            context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()

        return descriptor.use { fd ->
            runCatching {
                PdfRenderer(fd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, MAX_PAGES)
                    (0 until pageCount).mapNotNull { index -> renderPage(renderer, index) }
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): String? = runCatching {
        renderer.openPage(index).use { page ->
            val width = TARGET_WIDTH
            val height = (TARGET_WIDTH.toFloat() / page.width * page.height)
                .toInt()
                .coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PdfRenderer 不会自动铺底色，白底更接近真实观感（也让模型更好认字）
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val bytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                output.toByteArray()
            }
            bitmap.recycle()

            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }.getOrNull()
}