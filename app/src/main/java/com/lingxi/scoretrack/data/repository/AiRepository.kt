package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.remote.AiApi
import com.lingxi.scoretrack.data.remote.dto.ChatMessageDto
import com.lingxi.scoretrack.data.remote.dto.ChatRequestDto
import com.lingxi.scoretrack.data.secret.AiConfigStore
import com.lingxi.scoretrack.domain.model.AiModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 调用仓库。所有网络动作都在 IO 线程，返回 Result 由上层决定如何提示。
 */
@Singleton
class AiRepository @Inject constructor(
    private val api: AiApi,
    private val configStore: AiConfigStore
) {

    // ---------- 配置读写 ----------

    fun textConfig(): AiModelConfig = configStore.textConfig()

    fun visionConfig(): AiModelConfig = configStore.visionConfig()

    fun saveTextConfig(config: AiModelConfig) = configStore.saveTextConfig(config)

    fun saveVisionConfig(config: AiModelConfig) = configStore.saveVisionConfig(config)

    /** 清空配置后恢复 DeepSeek 默认地址 */
    fun resetTextConfig() = configStore.saveTextConfig(AiModelConfig.DefaultText)

    fun clearAllConfig() = configStore.clearAll()

    // ---------- 调用 ----------

    suspend fun chat(
        messages: List<ChatMessageDto>,
        config: AiModelConfig = configStore.textConfig()
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(
                IllegalStateException("AI 尚未配置：请先在设置中填写 Base URL、API Key 与模型名")
            )
        }
        runCatching {
            val response = api.chatCompletions(
                url = config.chatCompletionsUrl,
                authorization = "Bearer ${config.apiKey}",
                body = ChatRequestDto(
                    model = config.modelName,
                    messages = messages,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens
                )
            )
            response.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("模型返回内容为空")
        }
    }

    /** 「测试连接」按钮：发一个最小请求确认配置可用 */
    suspend fun testConnection(config: AiModelConfig = configStore.textConfig()): Result<String> =
        chat(
            messages = listOf(ChatMessageDto(role = "user", content = "ping")),
            config = config.copy(maxTokens = 16)
        )

    /** 单轮问答的便捷封装 */
    suspend fun ask(
        prompt: String,
        systemPrompt: String? = null
    ): Result<String> {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(ChatMessageDto(role = "system", content = systemPrompt))
            }
            add(ChatMessageDto(role = "user", content = prompt))
        }
        return chat(messages)
    }
}