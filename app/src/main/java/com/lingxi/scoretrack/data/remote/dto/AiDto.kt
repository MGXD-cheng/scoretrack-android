package com.lingxi.scoretrack.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** OpenAI 兼容协议的请求 / 响应模型。 */

/**
 * 一条消息。
 *
 * content 用 [JsonElement] 而不是 String —— 因为 OpenAI 兼容协议里它有两种形态：
 * - 纯文本：`"content": "你好"`
 * - 多模态：`"content": [{"type":"text",...},{"type":"image_url",...}]`
 *
 * 直接用 String 的话视觉模型根本没法调用。用工厂方法构造，别直接 new。
 */
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: JsonElement
) {
    companion object {
        /** 纯文本消息 */
        fun text(role: String, text: String): ChatMessageDto =
            ChatMessageDto(role = role, content = JsonPrimitive(text))

        /**
         * 多模态消息：文本 + **多张**图片。
         *
         * 一次请求就能带上多页 PDF —— 协议本身支持在 content 数组里
         * 塞多个 image_url，没必要拆成多次请求再合并结果。
         */
        fun withImages(role: String, text: String, imageDataUrls: List<String>): ChatMessageDto =
            ChatMessageDto(
                role = role,
                content = buildJsonArray {
                    addJsonObject {
                        put("type", "text")
                        put("text", text)
                    }
                    imageDataUrls.forEach { url ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", url)
                            }
                        }
                    }
                }
            )

        /** 单图便捷写法 */
        fun withImage(role: String, text: String, imageDataUrl: String): ChatMessageDto =
            withImages(role, text, listOf(imageDataUrl))
    }
}

/** 响应里的 content 通常是纯字符串，非字符串形态返回 null 而不是抛异常 */
val ChatMessageDto.plainText: String?
    get() = (content as? JsonPrimitive)?.contentOrNull

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