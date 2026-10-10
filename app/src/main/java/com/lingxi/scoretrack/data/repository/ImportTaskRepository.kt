package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.ImportTaskDao
import com.lingxi.scoretrack.data.local.entity.ImportTaskEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportTaskRepository @Inject constructor(
    private val importTaskDao: ImportTaskDao
) {

    fun observeByProfile(profileId: Long): Flow<List<ImportTaskEntity>> =
        importTaskDao.observeByProfile(profileId)

    suspend fun getById(id: Long): ImportTaskEntity? = importTaskDao.getById(id)

    suspend fun insert(task: ImportTaskEntity): Long = importTaskDao.insert(task)

    suspend fun update(task: ImportTaskEntity) = importTaskDao.update(task)

    suspend fun delete(task: ImportTaskEntity) = importTaskDao.delete(task)

    suspend fun deleteById(id: Long) = importTaskDao.deleteById(id)
}