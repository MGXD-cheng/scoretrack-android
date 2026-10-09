package com.lingxi.scoretrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lingxi.scoretrack.data.local.dao.AnalysisReportDao
import com.lingxi.scoretrack.data.local.dao.AppSettingDao
import com.lingxi.scoretrack.data.local.dao.ExamDao
import com.lingxi.scoretrack.data.local.dao.ImportTaskDao
import com.lingxi.scoretrack.data.local.dao.KnowledgePointDao
import com.lingxi.scoretrack.data.local.dao.MistakeDao
import com.lingxi.scoretrack.data.local.dao.PaperDao
import com.lingxi.scoretrack.data.local.dao.ProfileDao
import com.lingxi.scoretrack.data.local.dao.QuestionDao
import com.lingxi.scoretrack.data.local.dao.QuestionKnowledgeDao
import com.lingxi.scoretrack.data.local.dao.ScoreDao
import com.lingxi.scoretrack.data.local.dao.SubjectDao
import com.lingxi.scoretrack.data.local.dao.SyncDeviceDao
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.local.entity.AppSettingEntity
import com.lingxi.scoretrack.data.local.entity.ExamEntity
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
 * version 从 1 起步，exportSchema = true，后续迭代通过 Migration 升级。
 */
@Database(
    entities = [
        ProfileEntity::class,
        SubjectEntity::class,
        ExamEntity::class,
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
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun subjectDao(): SubjectDao
    abstract fun examDao(): ExamDao
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