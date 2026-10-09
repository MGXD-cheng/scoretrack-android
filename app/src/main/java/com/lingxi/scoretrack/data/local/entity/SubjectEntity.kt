package com.lingxi.scoretrack.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 科目。属于某个档案，满分与颜色可自定义。
 * 注：SQLite 中 `order` 是保留字，列名改为 `sort_order`，语义不变。
 */
@Entity(
    tableName = "subject",
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
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: Long,
    val name: String,
    val fullScore: Float,
    val color: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)