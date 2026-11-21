package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lingxi.scoretrack.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {

    @Query("SELECT value FROM app_setting WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_setting WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)

    /** 全量读取。备份/同步导出设置时用。 */
    @Query("SELECT * FROM app_setting")
    suspend fun getAllOnce(): List<AppSettingEntity>

    @Query("DELETE FROM app_setting WHERE `key` = :key")
    suspend fun remove(key: String)
}