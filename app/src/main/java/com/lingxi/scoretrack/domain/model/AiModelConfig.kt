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

        /**
         * DeepSeek 的视觉模型，2026-08 上线。
         *
         * 和文本模型**同地址、同 key** —— 用户手里那把 DeepSeek 的 key
         * 本来就能识图，只是需要换一个模型名。
         */
        const val DEFAULT_VISION_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_VISION_MODEL = "deepseek-v4-flash-vision-exp"

        val DefaultText = AiModelConfig(
            baseUrl = DEFAULT_TEXT_BASE_URL,
            modelName = DEFAULT_TEXT_MODEL
        )

        val DefaultVision = AiModelConfig(
            baseUrl = DEFAULT_VISION_BASE_URL,
            modelName = DEFAULT_VISION_MODEL
        )
    }
}