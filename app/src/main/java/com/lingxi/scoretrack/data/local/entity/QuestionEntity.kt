package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 题目：试卷中的一道小题。
 * type：选择 / 填空 / 解答；difficulty：易 / 中 / 难。
 */
@Entity(
    tableName = "question",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("paperId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val paperId: Long,
    val number: String,
    val type: String? = null,
    val fullScore: Float = 0f,
    val score: Float = 0f,
    val difficulty: String? = null,
    val content: String? = null,
    val answer: String? = null,
    val userAnswer: String? = null,
    val isCorrect: Boolean? = null,
    val wrongReason: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)