package com.lingxi.scoretrack.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lingxi.scoretrack.data.local.dao.AnalysisReportDao
import com.lingxi.scoretrack.data.local.dao.AppSettingDao
import com.lingxi.scoretrack.data.local.dao.ExamDao
import com.lingxi.scoretrack.data.local.dao.ExamGoalDao
import com.lingxi.scoretrack.data.local.dao.ImportTaskDao
import com.lingxi.scoretrack.data.local.dao.KnowledgePointDao
import com.lingxi.scoretrack.data.local.dao.MistakeDao
import com.lingxi.scoretrack.data.local.dao.PaperDao
import com.lingxi.scoretrack.data.local.dao.ProfileCleanupDao
import com.lingxi.scoretrack.data.local.dao.ProfileDao
import com.lingxi.scoretrack.data.local.dao.QuestionDao
import com.lingxi.scoretrack.data.local.dao.QuestionKnowledgeDao
import com.lingxi.scoretrack.data.local.dao.ScoreDao
import com.lingxi.scoretrack.data.local.dao.SubjectDao
import com.lingxi.scoretrack.data.local.dao.SyncDeviceDao
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.local.entity.AppSettingEntity
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.ImportTaskEntity
import com.lingxi.scoretrack.data.local.entity.KnowledgePointEntity
import com.lingxi.scoretrack.data.local.entity.MistakeEntity
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
import com.lingxi.scoretrack.data.local.entity.QuestionKnowledgeEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import com.lingxi.scoretrack.data.local.entity.SyncDeviceEntity

/**
 * 学迹本地数据库。
 * exportSchema = true 让 Room 把每版 schema 导出成 JSON，
 * AutoMigration 靠它自动生成建表 / 加索引的 SQL —— 不用手写、也不会写错。
 */
@Database(
    entities = [
        ProfileEntity::class,
        SubjectEntity::class,
        ExamEntity::class,
        ExamGoalEntity::class,
        ScoreEntity::class,
        PaperEntity::class,
        QuestionEntity::class,
        KnowledgePointEntity::class,
        QuestionKnowledgeEntity::class,
        MistakeEntity::class,
        AnalysisReportEntity::class,
        ImportTaskEntity::class,
        SyncDeviceEntity::class,
        AppSettingEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun profileCleanupDao(): ProfileCleanupDao
    abstract fun subjectDao(): SubjectDao
    abstract fun examDao(): ExamDao
    abstract fun examGoalDao(): ExamGoalDao
    abstract fun scoreDao(): ScoreDao
    abstract fun paperDao(): PaperDao
    abstract fun questionDao(): QuestionDao
    abstract fun knowledgePointDao(): KnowledgePointDao
    abstract fun questionKnowledgeDao(): QuestionKnowledgeDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun analysisReportDao(): AnalysisReportDao
    abstract fun importTaskDao(): ImportTaskDao
    abstract fun syncDeviceDao(): SyncDeviceDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val DB_NAME = "scoretrack.db"
    }
}