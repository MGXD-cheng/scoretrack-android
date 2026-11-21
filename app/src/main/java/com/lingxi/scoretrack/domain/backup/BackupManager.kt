package com.lingxi.scoretrack.domain.backup

import android.content.Context
import android.net.Uri
import com.lingxi.scoretrack.domain.sync.SnapshotExporter
import com.lingxi.scoretrack.domain.sync.SnapshotImporter
import com.lingxi.scoretrack.domain.sync.SyncSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导出/恢复的统计。
 */
data class BackupResult(
    val fileName: String = "",
    val recordCount: Int = 0,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * 本地文件备份。
 *
 * ## 为什么复用同步的 Snapshot
 * 「备份到文件」和「同步到另一台设备」在数据上**完全是同一件事** ——
 * 都是把整个数据库打成一个快照。搞两套格式只会带来两种 bug：
 * 一边能恢复、一边恢复不了，而且两份解析代码会各自演化。
 *
 * 所以这里直接复用 [SnapshotExporter] 和 [SnapshotImporter]，
 * 备份文件就是一份 `SyncSnapshot` 的 JSON。
 *
 * 顺带的好处：**备份文件可以直接被另一台设备恢复**，
 * 用户不想开 WiFi 同步时用微信把文件传过去就行。
 *
 * ## 为什么用 JSON 而不是复制 .db 文件
 * 复制数据库文件看起来更「完整」，但：
 * - 两台设备的 Room 版本可能不同，直接替换会 schema 校验失败，
 *   报错信息对用户来说完全无法理解
 * - .db 里有 sync_device / app_setting 这些**设备本地**的表，
 *   原样搬过去只会带来混乱
 * - JSON 可以在导入层做协议版本检查，明确告诉用户「请先升级 App」
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: SnapshotExporter,
    private val importer: SnapshotImporter
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 写出备份文件。
     *
     * @param uri 由 SAF（`CreateDocument`）返回的目标位置。
     *            用 SAF 而不是直接写 `/sdcard`：Android 10+ 的分区存储下
     *            应用无权随意写公共目录，而且用户也更想自己挑放哪儿
     *            （网盘同步目录、SD 卡、微信文件夹……）。
     */
    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = exporter.exportAll()

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                // 缩进过的 JSON 体积大一点，但用户万一用文本编辑器打开
                // 至少能看懂 —— 这是个备份文件，可读性比几 KB 重要
                val text = json.encodeToString(SyncSnapshot.serializer(), snapshot)
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: throw IllegalStateException("打不开这个位置，换一个文件夹试试")

            BackupResult(
                fileName = uri.lastPathSegment.orEmpty(),
                recordCount = snapshot.totalRecords
            )
        }.getOrElse { e ->
            BackupResult(error = e.message ?: "导出失败")
        }
    }

    /**
     * 从备份文件恢复。
     *
     * **是合并，不是覆盖** —— 已有的数据不会被删掉，重复的会被跳过。
     * 这点必须让用户知道，否则「恢复备份」这个词很容易被理解成
     * 「回到当时的那个状态」，然后发现旧数据还在而感到困惑。
     */
    suspend fun restore(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("读不到这个文件")

            if (text.isBlank()) throw IllegalStateException("文件是空的")

            val snapshot = try {
                json.decodeFromString(SyncSnapshot.serializer(), text)
            } catch (e: Exception) {
                // 解析失败最常见的原因就是选错了文件，给一句人话
                throw IllegalStateException("这个文件不是学迹的备份（或者已经损坏）")
            }

            val result = importer.import(snapshot)
            if (!result.isSuccess) {
                throw IllegalStateException(result.error.orEmpty())
            }

            BackupResult(
                fileName = uri.lastPathSegment.orEmpty(),
                recordCount = result.created + result.updated
            )
        }.getOrElse { e ->
            BackupResult(error = e.message ?: "恢复失败")
        }
    }

    /** 默认文件名，形如 `学迹-备份-2026-02-14.json` */
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "学迹-备份-$stamp.json"
    }
}