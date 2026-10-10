package com.lingxi.scoretrack.domain.model

/**
 * 单个模型（文本 / 视觉）的连接配置。
 * 默认目标为 DeepSeek（文本）；视觉模型由用户自行填写兼容 OpenAI 协议的地址。
 */
data class AiModelConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048,
    val enabled: Boolean = false
) {

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

    /** 去掉尾部斜杠，拼接 /chat/completions 用 */
    val chatCompletionsUrl: String
        get() = baseUrl.trimEnd('/') + "/chat/completions"

    companion object {
        const val DEFAULT_TEXT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_TEXT_MODEL = "deepseek-chat"
        const val DEFAULT_VISION_BASE_URL = ""
        const val DEFAULT_VISION_MODEL = ""

        val DefaultText = AiModelConfig(
            baseUrl = DEFAULT_TEXT_BASE_URL,
            modelName = DEFAULT_TEXT_MODEL
        )
    }
}