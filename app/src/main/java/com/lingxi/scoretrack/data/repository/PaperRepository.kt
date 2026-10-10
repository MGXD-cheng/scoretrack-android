package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.PaperDao
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaperRepository @Inject constructor(
    private val paperDao: PaperDao
) {

    fun observeByExam(examId: Long): Flow<List<PaperEntity>> = paperDao.observeByExam(examId)

    suspend fun getByExamAndSubject(examId: Long, subjectId: Long): PaperEntity? =
        paperDao.getByExamAndSubject(examId, subjectId)

    suspend fun getById(id: Long): PaperEntity? = paperDao.getById(id)

    suspend fun insert(paper: PaperEntity): Long = paperDao.insert(paper)

    suspend fun update(paper: PaperEntity) =
        paperDao.update(paper.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(paper: PaperEntity) = paperDao.delete(paper)

    suspend fun deleteById(id: Long) = paperDao.deleteById(id)
}