package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 知识点。层级：一级 → 二级 → 具体考点（parentId 指向上级）。
 * aliases 由 TypeConverter 序列化为 JSON 数组存储。
 * source：ai / manual。
 */
@Entity(
    tableName = "knowledge_point",
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
    indices = [Index("profileId"), Index("subjectId"), Index("parentId")]
)
data class KnowledgePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val subjectId: Long,
    val name: String,
    val aliases: List<String> = emptyList(),
    val parentId: Long? = null,
    val source: String = "ai",
    val updatedAt: Long = System.currentTimeMillis()
)