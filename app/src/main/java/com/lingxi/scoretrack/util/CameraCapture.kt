package com.lingxi.scoretrack.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * 拍照落盘的临时文件管理。
 *
 * 相机 App 需要往一个真实存在的 Uri 里写数据，而且从 Android 7.0 起
 * 直接传 `file://` 给别的 App 会抛 FileUriExposedException —— 必须走 FileProvider
 * 换成 `content://`，并临时授权给它写。
 *
 * 文件放在 cacheDir，因为系统可以随时回收缓存。
 */
object CameraCapture {

    private const val DIR_NAME = "camera"

    /** 与 AndroidManifest 里 provider 的 authorities 保持一致 */
    fun authorityOf(context: Context): String = "${context.packageName}.fileprovider"

    /**
     * 为一次拍照创建一个空文件，返回可交给相机的 Uri。
     *
     * 文件名带时间戳 —— 短时间内连拍两张时不会互相覆盖。
     */
    fun createImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, "sheet_${System.currentTimeMillis()}.jpg")
        // 事先建出空文件：相机打开输出流时目标存在最稳妥
        runCatching { file.createNewFile() }
        return FileProvider.getUriForFile(context, authorityOf(context), file)
    }

    /**
     * 把 Uri 的写权限授给所有能响应拍照的相机 App。
     *
     * **这一步不能省。** `ActivityResultContracts.TakePicture` 只负责把
     * Uri 塞进 `EXTRA_OUTPUT`，它不会加任何 GRANT 标志。相机是另一个进程，
     * 拿不到写权限就写不进文件 —— 更糟的是，部分厂商相机在这种情况下
     * 仍然返回 RESULT_OK，于是这边以为拍成功了，实际读到一个空文件。
     */
    fun grantWritePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        val probe = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        context.packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { info ->
                runCatching {
                    context.grantUriPermission(info.activityInfo.packageName, uri, flags)
                }
            }
    }

    /**
     * 清理拍照缓存。
     *
     * 识别完之后原图就没用了 —— 用户的成绩单照片留在缓存里既占地方，
     * 也让人心里不踏实。启动时扫一遍，把超过一天的清掉。
     */
    fun clearOldCaptures(context: Context, olderThanMillis: Long) {
        val dir = File(context.cacheDir, DIR_NAME)
        if (!dir.isDirectory) return

        val deadline = System.currentTimeMillis() - olderThanMillis
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < deadline }
            .forEach { runCatching { it.delete() } }
    }
}