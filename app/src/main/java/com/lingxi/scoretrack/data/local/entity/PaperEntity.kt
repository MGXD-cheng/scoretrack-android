package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 试卷：某次考试某科目的试卷结构。
 * sourceType：manual / photo / file。
 */
@Entity(
    tableName = "paper",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("examId"), Index("subjectId")]
)
data class PaperEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val examId: Long,
    val subjectId: Long,
    val title: String,
    val totalScore: Float,
    val sourceType: String = "manual",
    val sourceFileUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)