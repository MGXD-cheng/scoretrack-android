package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 导入任务。保留 AI 原始返回，便于排查提取错误。
 * status：待解析 / 解析中 / 待确认 / 已完成 / 失败。
 * 注意：仅存文件路径或原始文本，不存原始二进制文件。
 */
@Entity(
    tableName = "import_task",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class ImportTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val fileUrl: String? = null,
    val fileType: String,
    val importType: String,
    val status: String = "待解析",
    val aiRawResult: String? = null,
    val confirmedResult: String? = null,
    val filterTarget: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)