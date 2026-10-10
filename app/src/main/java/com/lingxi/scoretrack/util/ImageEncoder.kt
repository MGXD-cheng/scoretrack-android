package com.lingxi.scoretrack.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 把相册 / 相机的图片压成 base64 data URL，供视觉模型使用。
 *
 * 为什么要压：手机原图动辄 5–10MB，base64 还要再膨胀 33%，
 * 直接扔进请求体会又慢又容易超限。
 *
 * **为什么先把文件整个读进内存**：
 * FileProvider 的 `openInputStream` 返回的流**不支持 mark/reset**，
 * 而 `BitmapFactory.decodeStream` 在不可 seek 的流上解码某些格式
 * （尤其是现代相机默认输出的 HEIC）会直接失败。
 * 用 `decodeByteArray` 就完全绕开了这个问题 —— 图片本来也就几百 KB 到几 MB。
 */
object ImageEncoder {

    private const val MAX_SIDE = 1280
    private const val JPEG_QUALITY = 80

    /** 原样兜底时的体积上限，防止把超大文件塞进请求体 */
    private const val MAX_RAW_BYTES = 8 * 1024 * 1024

    fun toDataUrl(context: Context, uri: Uri): String? {
        val bytes = compress(context, uri) ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * 不做任何解码，直接把原始字节当 data URL。
     *
     * **兜底用。** 视觉模型对 HEIC、渐进式 JPEG 这类格式的容忍度往往比
     * Android 的 BitmapFactory 高 —— 我们自己解不开，不代表模型也读不了。
     * 与其直接报「解码失败」把人挡在门外，不如原样发过去试一次。
     *
     * @return null 表示文件读不出来，或者大到不适合直接发
     */
    fun toRawDataUrl(context: Context, uri: Uri, mimeType: String): String? {
        val raw = runCatching { readBytes(context, uri) }.getOrNull() ?: return null
        if (raw.isEmpty() || raw.size > MAX_RAW_BYTES) return null

        // 拿不到类型时按 jpeg 发 —— 绝大多数视觉模型都认
        val type = mimeType.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        return "data:$type;base64," + Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun compress(context: Context, uri: Uri): ByteArray? = runCatching {
        val raw = readBytes(context, uri) ?: return@runCatching null
        if (raw.isEmpty()) return@runCatching null

        // 1. 只读尺寸，算采样率（这一步不分配像素内存）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)

        // 2. 降采样解码
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
            ?: return@runCatching null

        // 3. 仍超标就再缩一次
        val finalBitmap = scaleDown(decoded)

        val bytes = ByteArrayOutputStream().use { output ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }

        if (finalBitmap !== decoded) decoded.recycle()
        finalBitmap.recycle()
        bytes
    }.getOrNull()

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_SIDE || height / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_SIDE) return bitmap
        val ratio = MAX_SIDE.toFloat() / longest
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}