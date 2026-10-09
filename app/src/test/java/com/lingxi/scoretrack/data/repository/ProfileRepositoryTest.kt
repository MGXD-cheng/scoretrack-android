package com.lingxi.scoretrack.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预置科目逻辑的单元测试（纯 JVM，不依赖 Android 运行时）。
 * 验证：新建档案 → 自动带出 9 门科目，且满分符合约定。
 */
class ProfileRepositoryTest {

    @Test
    fun defaultSubjects_returns_nine_subjects() {
        val list = ProfileRepository.defaultSubjects(profileId = 1L)
        assertEquals(9, list.size)
    }

    @Test
    fun chinese_math_english_have_full_score_150() {
        val map = ProfileRepository.defaultSubjects(1L).associate { it.name to it.fullScore }
        assertEquals(150f, map["语文"] ?: 0f, 0.0001f)
        assertEquals(150f, map["数学"] ?: 0f, 0.0001f)
        assertEquals(150f, map["英语"] ?: 0f, 0.0001f)
    }

    @Test
    fun other_six_subjects_have_full_score_100() {
        val list = ProfileRepository.defaultSubjects(1L)
        val others = list.filter { it.name !in listOf("语文", "数学", "英语") }
        assertEquals(6, others.size)
        assertTrue(others.all { it.fullScore == 100f })
    }

    @Test
    fun all_subjects_carry_profile_id_and_sequential_order() {
        val profileId = 42L
        val list = ProfileRepository.defaultSubjects(profileId)
        assertTrue(list.all { it.profileId == profileId })
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            list.map { it.sortOrder }
        )
    }

    @Test
    fun subject_names_are_unique() {
        val list = ProfileRepository.defaultSubjects(1L)
        assertEquals(9, list.map { it.name }.toSet().size)
    }

    @Test
    fun every_subject_has_a_color() {
        val list = ProfileRepository.defaultSubjects(1L)
        assertTrue(list.all { it.color.startsWith("#") && it.color.length == 7 })
    }
}