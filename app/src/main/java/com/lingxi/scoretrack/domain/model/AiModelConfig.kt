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
    /**
     * 最大输出 token。
     *
     * **不能给太小**。DeepSeek V4 系列是推理模型：它先写一段
     * `reasoning_content` 思考，再写正文，这两部分共用同一个额度。
     * 实测把 max_tokens 设成 16 时，16 个 token 全被思考吃掉，
     * 正文返回空字符串 —— 界面上就变成「模型返回内容为空」。
     *
     * 4096 是一次完整识别任务（一张成绩单 → 一整个 JSON）的实测够用量。
     */
    val maxTokens: Int = 4096,
    val enabled: Boolean = false
) {

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

    /** 去掉尾部斜杠，拼接 /chat/completions 用 */
    val chatCompletionsUrl: String
        get() = baseUrl.trimEnd('/') + "/chat/completions"

    companion object {
        const val DEFAULT_TEXT_BASE_URL = "https://api.deepseek.com"

        /**
         * 默认文本模型。
         *
         * 2026-11-21 对着 `GET /models` 实测确认了当前可用的模型：
         * - `deepseek-flash`      —— DeepSeek-V4.1-Flash，1M 上下文，**支持图片输入**
         * - `deepseek-v4-pro`     —— DeepSeek-V4-Pro，纯文本，推理更强
         *
         * 文本任务（分析报告、知识点归纳）选 Pro：要的是结论质量，不是速度。
         * 视觉任务选 Flash：它是目前唯一能读图的。
         *
         * 旧的 `deepseek-chat` 现在还能用，但那是服务端的兼容别名
         * （请求它实际会回 `"model":"deepseek-flash"`），随时可能下线 ——
         * 不能再把它当默认值。
         */
        const val DEFAULT_TEXT_MODEL = "deepseek-v4-pro"

        /**
         * 默认视觉模型。
         *
         * 和文本模型**同地址、同 key**。旧版本这里写的是
         * `deepseek-v4-flash-vision-exp`，实测该名字已不存在（同样被重定向到
         * flash），而且它会让「模型能不能读图」的判断逻辑失效 ——
         * 干脆直接用真名。
         */
        const val DEFAULT_VISION_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_VISION_MODEL = "deepseek-flash"

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