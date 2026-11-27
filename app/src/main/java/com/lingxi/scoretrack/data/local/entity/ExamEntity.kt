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
    /**
     * 成绩反思：用户自己写的「这次为什么考成这样」。
     *
     * 和 [remark] 分开存是有意的 —— remark 是「第三考场」「补考」这类
     * 客观信息，反思是主观复盘，两者不该挤在一个输入框里。
     */
    val reflection: String? = null,
    /** AI 基于这次成绩 + 上面的反思给出的建议。用户原话永远不会被它覆盖。 */
    val aiReflection: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)