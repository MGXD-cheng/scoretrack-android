package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 键值设置表。
 */
@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)