package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.local.dao.QuestionDao
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    private val questionDao: QuestionDao
) {

    fun observeByPaper(paperId: Long): Flow<List<QuestionEntity>> =
        questionDao.observeByPaper(paperId)

    suspend fun getByPaper(paperId: Long): List<QuestionEntity> = questionDao.getByPaper(paperId)

    suspend fun getById(id: Long): QuestionEntity? = questionDao.getById(id)

    suspend fun insert(question: QuestionEntity): Long = questionDao.insert(question)

    suspend fun insertAll(questions: List<QuestionEntity>): List<Long> =
        questionDao.insertAll(questions)

    suspend fun update(question: QuestionEntity) =
        questionDao.update(question.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(question: QuestionEntity) = questionDao.delete(question)

    suspend fun deleteByPaper(paperId: Long) = questionDao.deleteByPaper(paperId)
}