package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.SubjectDao
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val subjectDao: SubjectDao
) {

    fun observeByProfile(profileId: Long): Flow<List<SubjectEntity>> =
        subjectDao.observeByProfile(profileId)

    suspend fun getByProfile(profileId: Long): List<SubjectEntity> =
        subjectDao.getByProfile(profileId)

    suspend fun getById(id: Long): SubjectEntity? = subjectDao.getById(id)

    suspend fun getByName(profileId: Long, name: String): SubjectEntity? =
        subjectDao.getByName(profileId, name)

    suspend fun insert(subject: SubjectEntity): Long = subjectDao.insert(subject)

    suspend fun update(subject: SubjectEntity) = subjectDao.update(subject)

    suspend fun delete(subject: SubjectEntity) = subjectDao.delete(subject)
}