package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 成绩：某次考试某科目的得分、排名、平均分等。
 */
@Entity(
    tableName = "score",
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
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val examId: Long,
    val subjectId: Long,
    val score: Float,
    val fullScore: Float,
    val classAvg: Float? = null,
    val gradeAvg: Float? = null,
    val classRank: Int? = null,
    val gradeRank: Int? = null,
    val classTotal: Int? = null,
    val gradeTotal: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)