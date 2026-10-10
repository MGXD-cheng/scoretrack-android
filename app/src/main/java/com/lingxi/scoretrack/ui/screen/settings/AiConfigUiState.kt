package com.lingxi.scoretrack.ui.screen.settings

import com.lingxi.scoretrack.domain.model.AiModelConfig

/**
 * 表单用的模型配置。字段都是字符串，便于直接绑定到 TextField；
 * 保存时再统一做类型转换与兜底。
 */
data class AiModelForm(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val temperature: String = "0.3",
    val maxTokens: String = "2048",
    val enabled: Boolean = false
) {
    fun toConfig(): AiModelConfig = AiModelConfig(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        modelName = modelName.trim(),
        temperature = temperature.trim().toDoubleOrNull() ?: 0.3,
        maxTokens = maxTokens.trim().toIntOrNull() ?: 2048,
        enabled = enabled
    )

    companion object {
        fun from(config: AiModelConfig) = AiModelForm(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            modelName = config.modelName,
            temperature = config.temperature.toString(),
            maxTokens = config.maxTokens.toString(),
            enabled = config.enabled
        )
    }
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Running : ConnectionState
    data class Success(val message: String) : ConnectionState
    data class Failure(val message: String) : ConnectionState
}

data class AiConfigUiState(
    val isLoading: Boolean = true,
    val text: AiModelForm = AiModelForm(),
    val vision: AiModelForm = AiModelForm(),
    val textKeyVisible: Boolean = false,
    val visionKeyVisible: Boolean = false,
    val textTest: ConnectionState = ConnectionState.Idle,
    val visionTest: ConnectionState = ConnectionState.Idle,
    val savedHint: String? = null
) {
    val isTextConfigured: Boolean
        get() = text.baseUrl.isNotBlank() && text.apiKey.isNotBlank() && text.modelName.isNotBlank()
}