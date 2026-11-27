package com.lingxi.scoretrack.domain.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成绩反思的数据摘要。
 *
 * 摘要里最容易错的两件事：
 * 1. 得分率算错（150 分制和 100 分制混着比分数）
 * 2. 把用户写的反思丢了 —— 那就退化成一份通用报告，用户会觉得白写
 */
class ReflectionPromptTest {

    private fun entry(
        name: String,
        score: Float,
        full: Float,
        target: Float? = null
    ) = ReflectionPrompt.Entry(name, score, full, target)

    @Test
    fun `摘要保留用户原话`() {
        val digest = ReflectionPrompt.buildDigest(
            examName = "期中考试",
            examType = "期中",
            entries = listOf(entry("数学", 128f, 150f)),
            reflection = "最后两道大题没时间做"
        )
        assertTrue(digest.contains("最后两道大题没时间做"))
    }

    @Test
    fun `没写反思时明确说是空的而不是整段消失`() {
        val digest = ReflectionPrompt.buildDigest(
            examName = "期中考试",
            examType = "期中",
            entries = listOf(entry("数学", 128f, 150f)),
            reflection = "   "
        )
        // 留个占位，模型才知道「他没写」而不是「题目没给我」
        assertTrue(digest.contains("没有写"))
    }

    @Test
    fun `得分率按满分算，150 分制不会和 100 分制混`() {
        val digest = ReflectionPrompt.buildDigest(
            examName = "期中",
            examType = "期中",
            entries = listOf(entry("语文", 112f, 150f), entry("物理", 82f, 100f)),
            reflection = ""
        )
        assertTrue("应给出总分 194", digest.contains("194"))
        assertTrue("语文得分率应为 74%", digest.contains("74%"))
        assertTrue("物理得分率应为 82%", digest.contains("82%"))
    }

    @Test
    fun `最弱的一科按得分率点出来`() {
        val digest = ReflectionPrompt.buildDigest(
            examName = "期中",
            examType = "期中",
            entries = listOf(entry("语文", 112f, 150f), entry("物理", 82f, 100f)),
            reflection = ""
        )
        assertTrue(digest.contains("得分率最低：语文"))
    }

    @Test
    fun `有目标时逐科标出达标与否`() {
        val digest = ReflectionPrompt.buildDigest(
            examName = "期中",
            examType = "期中",
            entries = listOf(
                entry("数学", 128f, 150f, target = 120f),
                entry("英语", 95f, 150f, target = 120f)
            ),
            reflection = ""
        )
        assertTrue(digest.contains("达标"))
        assertTrue(digest.contains("未达标"))
    }

    @Test
    fun `没有目标时不出现达标字样`() {
        val digest = ReflectionPrompt.buildDigest(
            examName = "期中",
            examType = "期中",
            entries = listOf(entry("数学", 128f, 150f)),
            reflection = ""
        )
        assertFalse(digest.contains("达标"))
    }
}