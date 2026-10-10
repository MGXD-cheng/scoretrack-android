package com.lingxi.scoretrack.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** OpenAI 兼容协议的请求 / 响应模型。 */

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)

@Serializable
data class ChatResponseDto(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoiceDto> = emptyList(),
    val usage: ChatUsageDto? = null
)

@Serializable
data class ChatChoiceDto(
    val index: Int = 0,
    val message: ChatMessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)