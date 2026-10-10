package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.SyncDeviceDao
import com.lingxi.scoretrack.data.local.entity.SyncDeviceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncDeviceRepository @Inject constructor(
    private val syncDeviceDao: SyncDeviceDao
) {

    fun observeAll(): Flow<List<SyncDeviceEntity>> = syncDeviceDao.observeAll()

    suspend fun getByIp(ip: String): SyncDeviceEntity? = syncDeviceDao.getByIp(ip)

    suspend fun insert(device: SyncDeviceEntity): Long = syncDeviceDao.insert(device)

    suspend fun update(device: SyncDeviceEntity) = syncDeviceDao.update(device)

    suspend fun delete(device: SyncDeviceEntity) = syncDeviceDao.delete(device)
}