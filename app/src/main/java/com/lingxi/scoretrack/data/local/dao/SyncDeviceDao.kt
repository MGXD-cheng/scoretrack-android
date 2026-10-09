package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.SyncDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDeviceDao {

    @Query("SELECT * FROM sync_device ORDER BY lastSyncAt DESC")
    fun observeAll(): Flow<List<SyncDeviceEntity>>

    @Query("SELECT * FROM sync_device WHERE ip = :ip LIMIT 1")
    suspend fun getByIp(ip: String): SyncDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: SyncDeviceEntity): Long

    @Update
    suspend fun update(device: SyncDeviceEntity)

    @Delete
    suspend fun delete(device: SyncDeviceEntity)
}