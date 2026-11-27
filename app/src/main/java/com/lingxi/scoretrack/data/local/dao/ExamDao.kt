package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC")
    fun observeByProfile(profileId: Long): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC")
    suspend fun getByProfile(profileId: Long): List<ExamEntity>

    @Query("SELECT * FROM exam WHERE id = :id")
    suspend fun getById(id: Long): ExamEntity?

    /**
     * 同步合并用：同名 + 同日期视为同一次考试。
     *
     * 这是个**业务键**，不是数据库约束 —— 「高二上期中」在 11 月 8 号
     * 只可能有一场。用它来判重，重复同步才不会产生一场又一场的重复考试。
     */
    @Query(
        "SELECT * FROM exam WHERE profileId = :profileId AND name = :name AND date = :date LIMIT 1"
    )
    suspend fun findByNameAndDate(profileId: Long, name: String, date: Long): ExamEntity?

    @Query("SELECT * FROM exam WHERE id = :id")
    fun observeById(id: Long): Flow<ExamEntity?>

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(profileId: Long): ExamEntity?

    @Query("SELECT * FROM exam WHERE profileId = :profileId ORDER BY date DESC LIMIT 1")
    fun observeLatest(profileId: Long): Flow<ExamEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exam: ExamEntity): Long

    @Update
    suspend fun update(exam: ExamEntity)

    @Delete
    suspend fun delete(exam: ExamEntity)

    // ==================================================================
    // 级联统计与级联删除
    //
    // 删一场考试，会沿着 exam → paper → question
    // 这条链一路带走附属数据。外键上都写了 CASCADE，理论上 @Delete 一句就够了，
    // 但这里还是**显式按链条删**，理由和删科目时一样：
    // CASCADE 依赖 `PRAGMA foreign_keys = ON`，赌它永远开着不如自己动手。
    //
    // 更要紧的是：用户根本不知道「删考试」等于「删题目」。所以删除前
    // 必须先查一遍数量，把这些数字摆在确认框里 —— 这才是负责的做法。
    // ==================================================================

    @Query("SELECT COUNT(*) FROM paper WHERE examId = :examId")
    suspend fun countPapers(examId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM question WHERE paperId IN " +
            "(SELECT id FROM paper WHERE examId = :examId)"
    )
    suspend fun countQuestions(examId: Long): Int

    @Query(
        "DELETE FROM question WHERE paperId IN " +
            "(SELECT id FROM paper WHERE examId = :examId)"
    )
    suspend fun deleteQuestionsOfExam(examId: Long)

    @Query("DELETE FROM paper WHERE examId = :examId")
    suspend fun deletePapersOfExam(examId: Long)
}