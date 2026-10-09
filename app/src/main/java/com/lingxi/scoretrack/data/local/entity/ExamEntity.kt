package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 考试。type 取值：月考 / 期中 / 期末 / 模拟 / 随堂 / 其他。
 */
@Entity(
    tableName = "exam",
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
data class ExamEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val name: String,
    val type: String,
    val date: Long,
    val remark: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)