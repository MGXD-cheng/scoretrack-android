package com.lingxi.scoretrack.data.repository

import com.lingxi.scoretrack.data.remote.AiApi
import com.lingxi.scoretrack.data.remote.dto.ChatMessageDto
import com.lingxi.scoretrack.data.remote.dto.ChatRequestDto
import com.lingxi.scoretrack.data.remote.dto.plainText
import com.lingxi.scoretrack.data.secret.AiConfigStore
import com.lingxi.scoretrack.domain.ai.ExamSheetPrompt
import com.lingxi.scoretrack.domain.ai.RecognizedExamParser
import com.lingxi.scoretrack.domain.ai.VisionModelSupport
import com.lingxi.scoretrack.domain.model.AiModelConfig
import com.lingxi.scoretrack.domain.model.RecognizeResult
import com.lingxi.scoretrack.domain.model.RecognizedExam
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
            val choice = response.choices.firstOrNull()
            val text = choice?.message?.plainText
            if (text.isNullOrBlank()) {
                throw IllegalStateException(describeEmptyResponse(choice?.finishReason))
            }
            text
        }
    }

    /**
     * 正文为空时，给一句**能照着做**的话。
     *
     * 起因是实测踩到的：DeepSeek V4 是推理模型，先写 `reasoning_content`
     * 再写正文，两者共用 max_tokens。额度小时思考把额度吃光，正文是空字符串。
     * 原来的提示只有「模型返回内容为空」—— 用户看到这句只会以为是 key 坏了，
     * 然后去反复重填 Key。而真正该做的是把数字调大。
     */
    private fun describeEmptyResponse(finishReason: String?): String = when (finishReason) {
        "length" ->
            "模型把输出额度都用在了思考上，正文没写完。\n" +
                "到「设置 → AI 配置」把「最大输出」调大（建议 4096 以上），再试一次。"
        "content_filter" ->
            "内容被服务端的安全策略拦下了。换个说法再试，或者换一个模型。"
        else ->
            "模型返回了空内容（finish_reason=${finishReason ?: "未知"}）。\n" +
                "先点「测试连接」确认配置可用，再重试。"
    }

    /** 「测试连接」按钮：发一个最小请求确认配置可用 */
    suspend fun testConnection(config: AiModelConfig = configStore.textConfig()): Result<String> =
        chat(
            messages = listOf(ChatMessageDto.text(role = "user", text = "ping")),
            // 512 而不是 16：推理模型光是「想一下」就要几十个 token，
            // 给小了正文必然是空的，测试连接会假失败
            config = config.copy(maxTokens = 512)
        )

    /** 视觉模型的连通性测试：带一张 1×1 的图，确认多模态通道真的通 */
    suspend fun testVisionConnection(
        config: AiModelConfig = configStore.visionConfig()
    ): Result<String> = chat(
        messages = listOf(
            ChatMessageDto.withImage(
                role = "user",
                text = "ping",
                imageDataUrl = TINY_PNG_DATA_URL
            )
        ),
        // 同上：16 会被推理吃光，连「ping」都回不来
        config = config.copy(maxTokens = 512)
    )

    /**
     * 识别成绩单图片。
     *
     * 优先用视觉模型；没配视觉模型时**回退到文本模型** —— 因为不少多模态模型
     * 被用户配在了「文本」那一栏，直接报"未配置"会让人一头雾水。
     *
     * 但回退有一个前提：**那个模型得真能读图**。默认文本模型是 `deepseek-chat`，
     * 它只吃文本 —— 把图片发过去只会换回一个用户看不懂的报错。
     * 所以这里先把纯文本的候选项过滤掉。
     */
    suspend fun recognizeExamSheet(imageDataUrls: List<String>): Result<RecognizeResult> =
        withContext(Dispatchers.IO) {
            if (imageDataUrls.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("没有可识别的图片"))
            }
            val vision = configStore.visionConfig()
            val text = configStore.textConfig()

            val config = listOf(vision, text)
                .firstOrNull { it.isConfigured && !VisionModelSupport.isTextOnly(it.modelName) }
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        if (!vision.isConfigured && !text.isConfigured) {
                            "AI 尚未配置：请先到「设置 → AI 配置」填写模型信息"
                        } else {
                            VisionModelSupport.noVisionModelReason(vision.modelName, text.modelName)
                        }
                    )
                )

            runCatching {
                val response = api.chatCompletions(
                    url = config.chatCompletionsUrl,
                    authorization = "Bearer ${config.apiKey}",
                    body = ChatRequestDto(
                        model = config.modelName,
                        messages = listOf(
                            ChatMessageDto.text(role = "system", text = ExamSheetPrompt.SYSTEM),
                            ChatMessageDto.withImages(
                                role = "user",
                                text = ExamSheetPrompt.USER,
                                imageDataUrls = imageDataUrls
                            )
                        ),
                        // 提取任务要的是稳定复现，不是创意
                        temperature = 0.1,
                        maxTokens = 4096
                    )
                )
                val raw = response.choices.firstOrNull()?.message?.plainText
                    ?: throw IllegalStateException("模型没有返回内容")
                val exam = RecognizedExamParser.parse(raw)
                    ?: throw IllegalStateException("返回内容无法解析为成绩单，换个视觉模型试试")
                RecognizeResult(exam = exam, rawResponse = raw)
            }
        }

    /**
     * 从**文本**里提取成绩（CSV / Excel / Word 导出的表格）。
     *
     * 走文本模型：表格本身就是结构化数据，没必要浪费视觉模型的算力，
     * 而且文本通道更快、更便宜、也更准。
     */
    suspend fun extractExamFromText(tableText: String): Result<RecognizeResult> =
        withContext(Dispatchers.IO) {
            val config = configStore.textConfig()
            if (!config.isConfigured) {
                return@withContext Result.failure(
                    IllegalStateException("AI 尚未配置：请先到「设置 → AI 配置」填写模型信息")
                )
            }

            runCatching {
                val response = api.chatCompletions(
                    url = config.chatCompletionsUrl,
                    authorization = "Bearer ${config.apiKey}",
                    body = ChatRequestDto(
                        model = config.modelName,
                        messages = listOf(
                            ChatMessageDto.text(
                                role = "system",
                                text = ExamSheetPrompt.TEXT_SYSTEM
                            ),
                            ChatMessageDto.text(
                                role = "user",
                                text = ExamSheetPrompt.TEXT_USER + tableText
                            )
                        ),
                        temperature = 0.1,
                        maxTokens = 4096
                    )
                )
                val raw = response.choices.firstOrNull()?.message?.plainText
                    ?: throw IllegalStateException("模型没有返回内容")
                val exam = RecognizedExamParser.parse(raw)
                    ?: throw IllegalStateException("返回内容无法解析为成绩表，换个模型试试")
                RecognizeResult(exam = exam, rawResponse = raw)
            }
        }

    /** 单轮问答的便捷封装 */
    suspend fun ask(
        prompt: String,
        systemPrompt: String? = null
    ): Result<String> {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(ChatMessageDto.text(role = "system", text = systemPrompt))
            }
            add(ChatMessageDto.text(role = "user", text = prompt))
        }
        return chat(messages)
    }

    companion object {
        /** 1×1 透明 PNG，给视觉模型的连通性测试用 */
        private const val TINY_PNG_DATA_URL =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}