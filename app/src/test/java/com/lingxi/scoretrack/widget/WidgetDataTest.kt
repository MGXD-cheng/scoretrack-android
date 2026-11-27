package com.lingxi.scoretrack.widget

import com.lingxi.scoretrack.data.local.entity.ExamEntity
import com.lingxi.scoretrack.data.local.entity.ExamGoalEntity
import com.lingxi.scoretrack.data.local.entity.ProfileEntity
import com.lingxi.scoretrack.data.local.entity.ScoreEntity
import com.lingxi.scoretrack.data.local.entity.SubjectEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件的数据计算。
 *
 * 这层最容易出「看起来对但数字错」的问题：总分加错、最弱科按分数而不是
 * 得分率挑、没设目标却显示差多少分。而小组件在桌面上，用户扫一眼就走，
 * 错了也不会报错 —— 只会觉得「这 App 算得不准」。所以这里必须钉住。
 */
class WidgetDataTest {

    // 纯对象，不需要 DI 也不需要数据库
    private val calculator = WidgetCalculator

    private fun profile(name: String = "张小明") =
        ProfileEntity(id = 1L, name = name, isDefault = true)

    private fun subject(id: Long, name: String, full: Float = 150f) =
        SubjectEntity(
            id = id,
            profileId = 1L,
            name = name,
            fullScore = full,
            color = "#000000",
            sortOrder = id.toInt()
        )

    private fun exam(id: Long = 10L, name: String = "期中考试") =
        ExamEntity(id = id, profileId = 1L, name = name, type = "期中", date = 1_700_000_000_000L)

    private fun score(subjectId: Long, score: Float, full: Float, examId: Long = 10L) =
        ScoreEntity(examId = examId, subjectId = subjectId, score = score, fullScore = full)

    private fun goal(subjectId: Long?, target: Float) =
        ExamGoalEntity(profileId = 1L, subjectId = subjectId, targetScore = target)

    @Test
    fun `没有档案时返回空状态`() {
        val snapshot = calculator.build(null, null, emptyList(), emptyList(), emptyList(), 1)
        assertFalse(snapshot.hasProfile)
        assertFalse(snapshot.hasExam)
        assertEquals("暂无成绩", snapshot.scoreText)
    }

    @Test
    fun `总分与得分率按整场考试算`() {
        val subjects = listOf(subject(1, "语文"), subject(2, "数学"))
        val scores = listOf(score(1, 120f, 150f), score(2, 90f, 150f))
        val snapshot = calculator.build(profile(), exam(), subjects, scores, emptyList(), 1)

        assertTrue(snapshot.hasExam)
        assertEquals(210f, snapshot.totalScore, 0.01f)
        assertEquals(300f, snapshot.totalFull, 0.01f)
        assertEquals(0.7f, snapshot.rate!!, 0.001f)
        assertEquals("210 / 300", snapshot.scoreText)
    }

    @Test
    fun `最弱的科目按得分率挑，不是按分数`() {
        // 语文 112/150 = 74.7%，数学 118/150 = 78.7%，物理 82/100 = 82%
        // 分数最低的是物理（82），但得分率最低的是语文
        val subjects = listOf(subject(1, "语文"), subject(2, "数学"), subject(3, "物理", 100f))
        val scores = listOf(
            score(1, 112f, 150f),
            score(2, 118f, 150f),
            score(3, 82f, 100f)
        )
        val snapshot = calculator.build(profile(), exam(), subjects, scores, emptyList(), 1)

        assertEquals("语文", snapshot.lowestSubject)
    }

    @Test
    fun `设了目标但没达标时，计划指向最弱的一科`() {
        val subjects = listOf(subject(1, "语文"), subject(2, "数学"))
        val scores = listOf(score(1, 100f, 150f), score(2, 140f, 150f))
        val goals = listOf(goal(null, 600f), goal(1, 120f))
        val snapshot = calculator.build(profile(), exam(), subjects, scores, goals, 1)

        assertEquals(600f, snapshot.targetTotal!!, 0.01f)
        assertEquals(120f, snapshot.lowestSubjectTarget!!, 0.01f)
        assertTrue(
            "计划里应该点名语文并给出目标分：" + snapshot.plan,
            snapshot.plan.contains("语文") && snapshot.plan.contains("120")
        )
    }

    @Test
    fun `达到总分目标时，计划改成把目标往上挪`() {
        val subjects = listOf(subject(1, "语文"))
        val scores = listOf(score(1, 145f, 150f))
        val goals = listOf(goal(null, 140f))
        val snapshot = calculator.build(profile(), exam(), subjects, scores, goals, 1)

        assertTrue(snapshot.plan.contains("达成"))
        assertFalse("不该还提示「差多少分」", snapshot.plan.contains("还差"))
    }

    @Test
    fun `完全没有成绩时给的是先录一次而不是具体计划`() {
        val snapshot = calculator.build(
            profile(), null, listOf(subject(1, "语文")), emptyList(), emptyList(), 1
        )
        assertFalse(snapshot.hasExam)
        assertTrue(snapshot.plan.contains("先录一次"))
        assertNull(snapshot.lowestSubject)
    }

    @Test
    fun `同一自然日里激励语是固定的`() {
        // 刷新按钮连点、或者系统重绘，都不该让文案跳来跳去
        assertEquals(
            Encouragement.pick(100, onTrack = false),
            Encouragement.pick(100, onTrack = false)
        )
    }

    @Test
    fun `激励语按是否达标分两套且越界的天数不会崩`() {
        assertTrue(Encouragement.pick(1, onTrack = true).isNotBlank())
        assertTrue(Encouragement.pick(1, onTrack = false).isNotBlank())
        // 一年最多 366 天，但 pick 用 floorMod，任何整数都该安全
        assertTrue(Encouragement.pick(10_000, onTrack = false).isNotBlank())
        assertTrue(Encouragement.pick(-5, onTrack = true).isNotBlank())
    }
}