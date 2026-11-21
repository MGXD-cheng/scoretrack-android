package com.lingxi.scoretrack.domain.ai

import com.lingxi.scoretrack.domain.model.AiModelConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这个模型能不能读图」的判断。
 *
 * 这组用例的第一条是最要紧的：默认的视觉模型**必须**被判为能读图，
 * 默认的文本模型**必须**被判为不能。这两条一旦错了：
 * - 前者错了 → 用户配好了却被告知「没有可用视觉模型」
 * - 后者错了 → 图片被发给纯文本模型，换回一个看不懂的报错
 *
 * 而这正是 2026-11 真实发生过的事：服务商换了模型名，代码里的默认值
 * 和名单全都没跟上，而且没有任何测试会失败。
 */
class VisionModelSupportTest {

    @Test
    fun `默认视觉模型能读图`() {
        assertFalse(
            "默认视觉模型 ${AiModelConfig.DEFAULT_VISION_MODEL} 被判成纯文本了",
            VisionModelSupport.isTextOnly(AiModelConfig.DEFAULT_VISION_MODEL)
        )
        assertNull(VisionModelSupport.unsupportedReason(AiModelConfig.DEFAULT_VISION_MODEL))
    }

    @Test
    fun `默认文本模型是纯文本`() {
        assertTrue(
            "默认文本模型 ${AiModelConfig.DEFAULT_TEXT_MODEL} 被当成了多模态，图片会被发过去然后失败",
            VisionModelSupport.isTextOnly(AiModelConfig.DEFAULT_TEXT_MODEL)
        )
        assertNotNull(VisionModelSupport.unsupportedReason(AiModelConfig.DEFAULT_TEXT_MODEL))
    }

    @Test
    fun `旧版 DeepSeek 模型名仍被正确分类`() {
        // deepseek-chat 是纯文本（现在被服务端重定向到 flash，但名字本身不该放行）
        assertTrue(VisionModelSupport.isTextOnly("deepseek-chat"))
        // 旧的视觉名还能用，别因为改名单把它拦了
        assertFalse(VisionModelSupport.isTextOnly("deepseek-v4-flash-vision-exp"))
        // 更老的 vl 系列
        assertFalse(VisionModelSupport.isTextOnly("deepseek-vl2"))
    }

    @Test
    fun `名字里带 v4 不等于能读图`() {
        // v4-pro 名字里有「v4」，靠关键词猜会猜错 —— 必须靠名单
        assertTrue(VisionModelSupport.isTextOnly("deepseek-v4-pro"))
    }

    @Test
    fun `常见多模态模型放行`() {
        listOf("gpt-4o-mini", "gpt-4.1", "qwen-vl-max", "glm-4v", "gemini-2.0-flash")
            .forEach { assertFalse("$it 应该能读图", VisionModelSupport.isTextOnly(it)) }
    }

    @Test
    fun `判断不了的名字选择放行`() {
        // 宁可试一次失败，也不要因为不认识名字就把用户堵在门外
        assertFalse(VisionModelSupport.isTextOnly("some-new-provider-model-x"))
        // 空名不算纯文本（还没填模型名是另一类错误，不该在这里报）
        assertFalse(VisionModelSupport.isTextOnly(""))
    }

    @Test
    fun `没有可用视觉模型时的话术会给出具体可照做的建议`() {
        val message = VisionModelSupport.noVisionModelReason(
            visionModel = "deepseek-chat",
            textModel = "deepseek-chat"
        )
        assertTrue(message.contains("deepseek-chat"))
        assertTrue(message.contains(AiModelConfig.DEFAULT_VISION_MODEL))
    }
}