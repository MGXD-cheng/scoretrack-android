package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 档案 = 学生。支持多档案（自己 / 对比对象 / 帮别人记录）。
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val isDefault: Boolean = false,
    val className: String? = null,
    val gradeName: String? = null,
    val avatarPath: String? = null,
    val remark: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)