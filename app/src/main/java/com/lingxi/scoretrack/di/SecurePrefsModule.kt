package com.lingxi.scoretrack.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lingxi.scoretrack.data.secret.SECURE_PREFS_NAME
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * 加密存储模块。
 * API Key 等敏感信息一律走 EncryptedSharedPreferences（AES256-GCM 加密落盘）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurePrefsModule {

    @Provides
    @Singleton
    @Named(SECURE_PREFS_NAME)
    fun provideSecurePrefs(@ApplicationContext context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        @Suppress("DEPRECATION")
        return EncryptedSharedPreferences.create(
            context,
            "scoretrack_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}