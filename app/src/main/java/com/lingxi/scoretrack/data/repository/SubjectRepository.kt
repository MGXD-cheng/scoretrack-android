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

    suspend fun insertAll(subjects: List<SubjectEntity>): List<Long> =
        subjectDao.insertAll(subjects)

    suspend fun update(subject: SubjectEntity) = subjectDao.update(subject)

    /**
     * 按传入顺序重排科目，并把 sort_order 规范化为 0..n-1。
     *
     * 为什么不直接交换两个科目的 sort_order：历史数据里 sort_order 可能重复
     * （早期版本新建科目时都写 0），交换两个一样大的数字等于没动，用户会
     * 觉得「点了向上箭头但没反应」。整体重排没有这个问题。
     */
    suspend fun reorder(ordered: List<SubjectEntity>) {
        subjectDao.updateAll(ordered.mapIndexed { index, subject ->
            subject.copy(sortOrder = index)
        })
    }

    /** 该科目下已有多少条成绩（删除前提示用） */
    suspend fun countScores(subjectId: Long): Int = subjectDao.countScores(subjectId)

    /** 删除科目，连带清掉它的成绩（不依赖外键级联是否开启） */
    suspend fun deleteWithScores(subject: SubjectEntity) {
        subjectDao.deleteScoresOf(subject.id)
        subjectDao.delete(subject)
    }

    suspend fun delete(subject: SubjectEntity) = subjectDao.delete(subject)

    /** 该档案下一个可用的 sort_order */
    suspend fun nextSortOrder(profileId: Long): Int =
        (subjectDao.getByProfile(profileId).maxOfOrNull { it.sortOrder } ?: -1) + 1
}