package com.lingxi.scoretrack.data.secret

import android.content.SharedPreferences
import com.lingxi.scoretrack.domain.model.AiModelConfig
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** 加密 SharedPreferences 的注入标识（定义在 data 层，避免反向依赖 di 层） */
const val SECURE_PREFS_NAME = "secure_prefs"

/**
 * AI 配置读写。底层是 EncryptedSharedPreferences，API Key 以 AES256 加密落盘，
 * 任何情况下都不会以明文写入普通存储。
 */
@Singleton
class AiConfigStore @Inject constructor(
    @Named(SECURE_PREFS_NAME)
    private val prefs: SharedPreferences
) {

    fun textConfig(): AiModelConfig = read(PREFIX_TEXT)

    fun saveTextConfig(config: AiModelConfig) = write(PREFIX_TEXT, config)

    /**
     * 读视觉模型配置。
     *
     * 如果用户**没单独配**视觉模型，但文本模型用的是 DeepSeek，
     * 就自动补一个可用的视觉配置 —— **同地址、同 key，只换模型名**。
     *
     * 理由：换服务商的门槛远高于换个模型名。用户既然已经在用 DeepSeek，
     * 就没必要为了识别一张成绩单再去注册第二个账号。
     * 其他服务商不做这种推断 —— 我们没法假设它也有对应的视觉模型。
     */
    fun visionConfig(): AiModelConfig {
        val stored = read(PREFIX_VISION)
        if (stored.isConfigured) return stored
        return deriveVisionFromText()
    }

    fun saveVisionConfig(config: AiModelConfig) = write(PREFIX_VISION, config)

    private fun deriveVisionFromText(): AiModelConfig {
        val text = textConfig()
        val isDeepSeek = text.baseUrl.contains("deepseek", ignoreCase = true)
        if (!text.isConfigured || !isDeepSeek) return AiModelConfig()

        return AiModelConfig(
            baseUrl = text.baseUrl,
            apiKey = text.apiKey,
            modelName = AiModelConfig.DEFAULT_VISION_MODEL,
            temperature = text.temperature,
            maxTokens = text.maxTokens,
            enabled = true
        )
    }

    /** 是否已经至少配置好了文本模型（AI 功能可用性的前置判断） */
    fun hasUsableTextModel(): Boolean = textConfig().isConfigured

    private fun read(prefix: String): AiModelConfig = AiModelConfig(
        baseUrl = prefs.getString(prefix + KEY_BASE_URL, "").orEmpty(),
        apiKey = prefs.getString(prefix + KEY_API_KEY, "").orEmpty(),
        modelName = prefs.getString(prefix + KEY_MODEL, "").orEmpty(),
        temperature = prefs.getString(prefix + KEY_TEMPERATURE, "0.3")
            ?.toDoubleOrNull() ?: 0.3,
        maxTokens = prefs.getString(prefix + KEY_MAX_TOKENS, "2048")
            ?.toIntOrNull() ?: 2048,
        enabled = prefs.getBoolean(prefix + KEY_ENABLED, false)
    )

    private fun write(prefix: String, config: AiModelConfig) {
        prefs.edit()
            .putString(prefix + KEY_BASE_URL, config.baseUrl)
            .putString(prefix + KEY_API_KEY, config.apiKey)
            .putString(prefix + KEY_MODEL, config.modelName)
            .putString(prefix + KEY_TEMPERATURE, config.temperature.toString())
            .putString(prefix + KEY_MAX_TOKENS, config.maxTokens.toString())
            .putBoolean(prefix + KEY_ENABLED, config.enabled)
            .apply()
    }

    /** 清空全部 AI 配置（危险操作，需用户确认） */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFIX_TEXT = "text_"
        private const val PREFIX_VISION = "vision_"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_ENABLED = "enabled"
    }
}