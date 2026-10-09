package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 题目 ↔ 知识点 多对多关联表。
 */
@Entity(
    tableName = "question_knowledge",
    primaryKeys = ["questionId", "knowledgePointId"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgePointEntity::class,
            parentColumns = ["id"],
            childColumns = ["knowledgePointId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("knowledgePointId")]
)
data class QuestionKnowledgeEntity(
    val questionId: Long,
    val knowledgePointId: Long
)