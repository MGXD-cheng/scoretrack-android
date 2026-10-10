package com.lingxi.scoretrack.di

import android.content.Context
import androidx.room.Room
import com.lingxi.scoretrack.data.local.AppDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .build()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideProfileCleanupDao(db: AppDatabase): ProfileCleanupDao = db.profileCleanupDao()

    @Provides
    fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()

    @Provides
    fun provideExamDao(db: AppDatabase): ExamDao = db.examDao()

    @Provides
    fun provideExamGoalDao(db: AppDatabase): ExamGoalDao = db.examGoalDao()

    @Provides
    fun provideScoreDao(db: AppDatabase): ScoreDao = db.scoreDao()

    @Provides
    fun providePaperDao(db: AppDatabase): PaperDao = db.paperDao()

    @Provides
    fun provideQuestionDao(db: AppDatabase): QuestionDao = db.questionDao()

    @Provides
    fun provideKnowledgePointDao(db: AppDatabase): KnowledgePointDao = db.knowledgePointDao()

    @Provides
    fun provideQuestionKnowledgeDao(db: AppDatabase): QuestionKnowledgeDao = db.questionKnowledgeDao()

    @Provides
    fun provideMistakeDao(db: AppDatabase): MistakeDao = db.mistakeDao()

    @Provides
    fun provideAnalysisReportDao(db: AppDatabase): AnalysisReportDao = db.analysisReportDao()

    @Provides
    fun provideImportTaskDao(db: AppDatabase): ImportTaskDao = db.importTaskDao()

    @Provides
    fun provideSyncDeviceDao(db: AppDatabase): SyncDeviceDao = db.syncDeviceDao()

    @Provides
    fun provideAppSettingDao(db: AppDatabase): AppSettingDao = db.appSettingDao()
}