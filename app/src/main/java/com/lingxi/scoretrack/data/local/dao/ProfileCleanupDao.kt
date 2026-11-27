package com.lingxi.scoretrack.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * 档案级联清理。
 *
 * 为什么不用外键的 ON DELETE CASCADE？
 * 实体上虽然都声明了 CASCADE，但级联是否真的生效取决于运行时的
 * `PRAGMA foreign_keys`（SQLite 默认为关，是否开启跟初始化路径有关）。
 * 「删档案」是不可逆操作，不能赌这个开关 —— 这里用显式 DELETE，
 * 按依赖顺序从叶子往根删，行为完全可控。
 *
 * 删除顺序（叶子 → 根）：
 * question → paper → score
 * → analysis_report / import_task → subject → exam → profile
 */
@Dao
interface ProfileCleanupDao {

    /** 题目（挂 paper） */
    @Query(
        "DELETE FROM question WHERE paperId IN (" +
            "SELECT id FROM paper WHERE examId IN (" +
            "SELECT id FROM exam WHERE profileId = :profileId))"
    )
    suspend fun deleteQuestions(profileId: Long)

    /** 试卷（挂 exam + subject） */
    @Query(
        "DELETE FROM paper WHERE examId IN (" +
            "SELECT id FROM exam WHERE profileId = :profileId)"
    )
    suspend fun deletePapers(profileId: Long)

    /** 成绩（挂 exam + subject） */
    @Query(
        "DELETE FROM score WHERE examId IN (" +
            "SELECT id FROM exam WHERE profileId = :profileId)"
    )
    suspend fun deleteScores(profileId: Long)

    @Query("DELETE FROM analysis_report WHERE profileId = :profileId")
    suspend fun deleteAnalysisReports(profileId: Long)

    @Query("DELETE FROM import_task WHERE profileId = :profileId")
    suspend fun deleteImportTasks(profileId: Long)

    @Query("DELETE FROM subject WHERE profileId = :profileId")
    suspend fun deleteSubjects(profileId: Long)

    @Query("DELETE FROM exam WHERE profileId = :profileId")
    suspend fun deleteExams(profileId: Long)
}