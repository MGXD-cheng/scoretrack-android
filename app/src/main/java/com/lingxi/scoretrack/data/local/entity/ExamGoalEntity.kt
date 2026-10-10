package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 考试目标。
 *
 * `subjectId = null` 表示**总分目标**，否则是某一科的目标分。
 *
 * 没有用「profileId + subjectId」唯一索引：SQLite 的 UNIQUE 约束
 * 认为多个 NULL 互不相同，总分目标会重复插进去。改用「先查再写」保证唯一。
 */
@Entity(
    tableName = "exam_goal",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index("subjectId")]
)
data class ExamGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    /** null = 总分目标 */
    val subjectId: Long? = null,
    val targetScore: Float,
    /** 目标名次（可选）。留空表示只盯分数不盯排名 */
    val targetRank: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)