package com.lingxi.scoretrack.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.lingxi.scoretrack.data.local.dao.AnalysisReportDao
import com.lingxi.scoretrack.data.local.dao.AppSettingDao
import com.lingxi.scoretrack.data.local.dao.ExamDao
import com.lingxi.scoretrack.data.local.dao.ExamGoalDao
import com.lingxi.scoretrack.data.local.dao.ImportTaskDao
import com.lingxi.scoretrack.data.local.dao.PaperDao
import com.lingxi.scoretrack.data.local.dao.ProfileCleanupDao
import com.lingxi.scoretrack.data.local.dao.ProfileDao
import com.lingxi.scoretrack.data.local.dao.QuestionDao
import com.lingxi.scoretrack.data.local.dao.ScoreDao
import com.lingxi.scoretrack.data.local.dao.SubjectDao
import com.lingxi.scoretrack.data.local.dao.SyncDeviceDao
import com.lingxi.scoretrack.data.local.entity.AnalysisReportEntity
import com.lingxi.scoretrack.data.local.entity.AppSettingEntity
import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.ImportTaskEntity
import com.lingxi.scoretrack.data.local.entity.PaperEntity
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.local.entity.QuestionEntity
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
        AnalysisReportEntity::class,
        ImportTaskEntity::class,
        SyncDeviceEntity::class,
        AppSettingEntity::class
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        // v3 移除了错题 / 知识点三张表。删表必须显式声明（见 DeleteLegacyTablesSpec），
        // Room 不会替我们猜是故意删的还是漏写了。
        AutoMigration(from = 2, to = 3, spec = DeleteLegacyTablesSpec::class)
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
    abstract fun analysisReportDao(): AnalysisReportDao
    abstract fun importTaskDao(): ImportTaskDao
    abstract fun syncDeviceDao(): SyncDeviceDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val DB_NAME = "scoretrack.db"
    }
}

/**
 * v3 的删表迁移。
 *
 * Room 能自动推断「加表 / 加列」，但**删表必须显式声明** —— 它不会替
 * 我们猜「这张表是故意删的，还是漏写了」。顺序从引用方指向被引用方，
 * 和建表时正好相反。
 *
 * 放在文件顶层而不是 AppDatabase 的嵌套类：@Database 注解在 KSP 阶段解析时
 * 还看不到嵌套类，会报「referenced type is not present」。
 */
@DeleteTable.Entries(
    DeleteTable(tableName = "question_knowledge"),
    DeleteTable(tableName = "mistake"),
    DeleteTable(tableName = "knowledge_point")
)
class DeleteLegacyTablesSpec : AutoMigrationSpec