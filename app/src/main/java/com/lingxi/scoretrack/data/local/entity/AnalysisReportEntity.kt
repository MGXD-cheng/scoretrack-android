package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分析报告。content 为 Markdown 原文。
 * type：成绩趋势 / 试卷诊断 / 知识点 / 综合。
 */
@Entity(
    tableName = "analysis_report",
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
data class AnalysisReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val examId: Long? = null,
    val type: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)