package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Query("SELECT * FROM subject WHERE profileId = :profileId ORDER BY sort_order ASC, id ASC")
    fun observeByProfile(profileId: Long): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subject WHERE profileId = :profileId ORDER BY sort_order ASC, id ASC")
    suspend fun getByProfile(profileId: Long): List<SubjectEntity>

    @Query("SELECT * FROM subject WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subject WHERE profileId = :profileId AND name = :name LIMIT 1")
    suspend fun getByName(profileId: Long, name: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<SubjectEntity>): List<Long>

    @Update
    suspend fun update(subject: SubjectEntity)

    /** 批量重写排序用。列表下标即新的 sort_order */
    @Update
    suspend fun updateAll(subjects: List<SubjectEntity>)

    /**
     * 该科目下有多少条成绩。
     *
     * 删除科目时 score 表的级联会把成绩一并删掉（外键 CASCADE），
     * 所以在确认框里必须把这个数字告诉用户 —— 「删一门课」听起来无害，
     * 「顺手删掉 24 条期末成绩」就不一样了。
     */
    @Query("SELECT COUNT(*) FROM score WHERE subjectId = :subjectId")
    suspend fun countScores(subjectId: Long): Int

    /**
     * 显式删除该科目的全部成绩。
     *
     * 理论上 score 表的外键 CASCADE 会替我们做这件事，但外键约束依赖
     * `PRAGMA foreign_keys=ON` —— 一旦哪天换了数据库配置、或者走了
     * 别的迁移路径把它关掉，删除科目就会留下一堆孤儿成绩行
     * （图表上会出现科目已不存在、数据还在幽灵条目）。
     * 显式删除不受这个假设影响，代价只是一条 SQL。
     */
    @Query("DELETE FROM score WHERE subjectId = :subjectId")
    suspend fun deleteScoresOf(subjectId: Long)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}