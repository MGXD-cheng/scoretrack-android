package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.AnalysisReportDao
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisReportRepository @Inject constructor(
    private val analysisReportDao: AnalysisReportDao
) {

    fun observeByProfile(profileId: Long): Flow<List<AnalysisReportEntity>> =
        analysisReportDao.observeByProfile(profileId)

    suspend fun getByProfile(profileId: Long): List<AnalysisReportEntity> =
        analysisReportDao.getByProfile(profileId)

    suspend fun getById(id: Long): AnalysisReportEntity? = analysisReportDao.getById(id)

    suspend fun getLatest(profileId: Long): AnalysisReportEntity? =
        analysisReportDao.getLatest(profileId)

    suspend fun insert(report: AnalysisReportEntity): Long = analysisReportDao.insert(report)

    suspend fun delete(report: AnalysisReportEntity) = analysisReportDao.delete(report)

    suspend fun deleteById(id: Long) = analysisReportDao.deleteById(id)
}