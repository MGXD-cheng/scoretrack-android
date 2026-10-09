package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.ExamDao
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamRepository @Inject constructor(
    private val examDao: ExamDao
) {

    fun observeByProfile(profileId: Long): Flow<List<ExamEntity>> =
        examDao.observeByProfile(profileId)

    fun observeById(id: Long): Flow<ExamEntity?> = examDao.observeById(id)

    fun observeLatest(profileId: Long): Flow<ExamEntity?> = examDao.observeLatest(profileId)

    suspend fun getByProfile(profileId: Long): List<ExamEntity> = examDao.getByProfile(profileId)

    suspend fun getById(id: Long): ExamEntity? = examDao.getById(id)

    suspend fun getLatest(profileId: Long): ExamEntity? = examDao.getLatest(profileId)

    suspend fun insert(exam: ExamEntity): Long = examDao.insert(exam)

    suspend fun update(exam: ExamEntity) =
        examDao.update(exam.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(exam: ExamEntity) = examDao.delete(exam)
}