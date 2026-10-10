package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.AppSettingDao
import com.lingxi.scoretrack.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量键值设置。适合存"当前档案 id""是否首次启动"这类小状态。
 */
@Singleton
class AppSettingRepository @Inject constructor(
    private val appSettingDao: AppSettingDao
) {

    suspend fun get(key: String): String? = appSettingDao.get(key)

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean =
        appSettingDao.get(key)?.toBooleanStrictOrNull() ?: default

    suspend fun getLong(key: String, default: Long = 0L): Long =
        appSettingDao.get(key)?.toLongOrNull() ?: default

    fun observe(key: String): Flow<String?> = appSettingDao.observe(key)

    suspend fun put(key: String, value: String) =
        appSettingDao.put(AppSettingEntity(key, value))

    suspend fun putBoolean(key: String, value: Boolean) = put(key, value.toString())

    suspend fun putLong(key: String, value: Long) = put(key, value.toString())

    suspend fun remove(key: String) = appSettingDao.remove(key)

    companion object {
        const val KEY_CURRENT_PROFILE_ID = "current_profile_id"
        const val KEY_FIRST_LAUNCH = "first_launch"
    }
}