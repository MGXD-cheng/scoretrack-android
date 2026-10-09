package com.lingxi.scoretrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 局域网同步设备（P1 使用）。
 */
@Entity(tableName = "sync_device")
data class SyncDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val deviceName: String,
    val ip: String,
    val lastSyncAt: Long? = null,
    val syncToken: String = ""
)