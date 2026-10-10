package com.lingxi.scoretrack.domain.ai

/**
 * 「这个模型能不能读图」的判断。
 *
 * 双向名单：
 * 1. [KNOWN_VISION_PREFIXES] —— **明确支持**图片输入的模型，直接放行
 * 2. [TEXT_ONLY_PREFIXES]   —— **明确不支持**的，拦下来并给出人话提示
 * 3. 都不命中时，再看名字里有没有视觉关键词；仍然判断不了就放行
 *
 * 为什么值得维护第 1 条：只靠「名字里有没有 vision」是运气。
 * 模型命名千奇百怪，猜错一次的代价是用户对着一张成绩单反复重试。
 *
 * ⚠️ 名单需要跟着服务商更新。写这个文件时（2026-10）的依据：
 * DeepSeek 于 2026-08-21 上线了 `deepseek-v4-flash-vision-exp`。
 */
object VisionModelSupport {

    /** 明确支持图片输入 */
    private val KNOWN_VISION_PREFIXES = listOf(
        // DeepSeek：2026-08 上线，与 V4-Flash 同价，同地址同 key
        "deepseek-v4-flash-vision",
        "deepseek-vl",
        // OpenAI
        "gpt-4o",
        "gpt-4.1",
        "gpt-4-turbo",
        "o3",
        "o4",
        // 智谱
        "glm-4v",
        "glm-4.5v",
        // 通义千问
        "qwen-vl",
        "qwen2-vl",
        "qwen2.5-vl",
        "qwen3-vl",
        // 月之暗面
        "moonshot-v1-8k-vision",
        "moonshot-v1-32k-vision",
        "moonshot-v1-128k-vision",
        // Google
        "gemini-1.5",
        "gemini-2",
        // Anthropic
        "claude-3",
        "claude-4",
        // 阶跃星辰
        "step-1v"
    )

    /** 明确**不支持**图片输入 */
    private val TEXT_ONLY_PREFIXES = listOf(
        "deepseek-chat",
        "deepseek-reasoner",
        "deepseek-coder",
        "gpt-3.5",
        "text-davinci",
        "moonshot-v1-8k",
        "moonshot-v1-32k",
        "moonshot-v1-128k"
    )

    /** 判断不了时，名字里出现这些词就姑且当作能读图 */
    private val VISION_HINTS = listOf("vision", "-vl", "4v", "4o", "omni", "multimodal")

    fun isTextOnly(modelName: String): Boolean {
        val normalized = modelName.trim().lowercase()
        if (normalized.isEmpty()) return false

        if (KNOWN_VISION_PREFIXES.any { normalized.startsWith(it) }) return false
        if (VISION_HINTS.any { normalized.contains(it) }) return false

        return TEXT_ONLY_PREFIXES.any { normalized.startsWith(it) }
    }

    /**
     * 给用户看的解释。
     *
     * **优先推荐他手里那把 key 能直接用的模型** —— 换服务商的门槛
     * 远高于换个模型名，能复用就别让人去注册第二家。
     *
     * @return null 表示看起来能用
     */
    fun unsupportedReason(modelName: String): String? {
        if (!isTextOnly(modelName)) return null
        return "「$modelName」是纯文本模型，读不了图片。\n" +
            "如果你用的是 DeepSeek，把模型名改成 " +
            "${com.lingxi.scoretrack.domain.model.AiModelConfig.DEFAULT_VISION_MODEL} " +
            "即可 —— 同一个地址、同一把 key。\n" +
            "其他可选：gpt-4o-mini、qwen-vl-max、glm-4v。"
    }

    /** 一个可用的视觉模型都没有时的话术 */
    fun noVisionModelReason(
        visionModel: String,
        textModel: String
    ): String = buildString {
        append("图片识别需要一个能读图的模型，但当前没有可用的。\n")
        val culprit = visionModel.ifBlank { textModel }
        if (culprit.isNotBlank()) {
            append("当前候选「")
            append(culprit)
            append("」是纯文本模型。\n")
        }
        append("如果你用的是 DeepSeek，到「设置 → AI 配置 → 视觉模型」把模型名填成 ")
        append(com.lingxi.scoretrack.domain.model.AiModelConfig.DEFAULT_VISION_MODEL)
        append(" 就行（地址和 key 与文本模型相同）。")
    }
}