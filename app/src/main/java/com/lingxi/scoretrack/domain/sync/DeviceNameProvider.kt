package com.lingxi.scoretrack.domain.sync

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 给同步快照提供一个人类可读的设备名。
 *
 * 「两台手机都叫‘我的手机’」是同步功能里最现实的困惑来源 ——
 * 用户在目标端看到「来自 Pixel 7」才知道自己没选错源。
 */
@Singleton
class DeviceNameProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun get(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()

        // 有些厂商的 MODEL 已经带上了品牌名（如 "Xiaomi 14"），
        // 再拼一次会变成 "Xiaomi Xiaomi 14"
        val combined = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }

        return combined.ifBlank { "Android 设备" }
    }
}