package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 错题本条目。
 * reviewStatus：未复习 / 复习中 / 已掌握。
 */
@Entity(
    tableName = "mistake",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index("questionId")]
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val questionId: Long,
    val note: String? = null,
    val reviewStatus: String = "未复习",
    val reviewCount: Int = 0,
    val lastReviewAt: Long? = null
)