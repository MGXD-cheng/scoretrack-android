package com.lingxi.scoretrack.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错题文本解析的单元测试（纯 JVM）。
 *
 * 这是用户直接手打的内容，格式一定会歪 —— 能容忍的歪必须容忍，
 * 不能容忍的必须给出**能看懂的行号**，而不是默默吞掉。
 */
class MistakeTextParserTest {

    @Test
    fun parses_full_row() {
        val outcome = MistakeTextParser.parse(
            "数学,选择题,若 f(1)=? A.1 B.2,C,A,代入错了"
        )
        assertEquals(1, outcome.rows.size)
        assertEquals("数学", outcome.rows[0].subjectName)
        assertEquals("选择题", outcome.rows[0].type)
        assertEquals("C", outcome.rows[0].answer)
        assertEquals("A", outcome.rows[0].userAnswer)
        assertEquals("代入错了", outcome.rows[0].wrongReason)
        assertTrue(outcome.errors.isEmpty())
    }

    @Test
    fun skips_blank_and_comment_lines() {
        val text = """
            # 这是注释，不该变成题目

            数学,填空题,sin30° = ____,1/2
            # 又一条注释
        """.trimIndent()
        val outcome = MistakeTextParser.parse(text)
        assertEquals(1, outcome.rows.size)
        assertTrue(outcome.errors.isEmpty())
    }

    @Test
    fun missing_trailing_columns_are_allowed() {
        val outcome = MistakeTextParser.parse("英语,选择题,He ____ to school.,B")
        assertEquals(1, outcome.rows.size)
        assertEquals("B", outcome.rows[0].answer)
        assertEquals("", outcome.rows[0].userAnswer)
        assertEquals("", outcome.rows[0].wrongReason)
    }

    @Test
    fun too_few_columns_reports_line_number() {
        val outcome = MistakeTextParser.parse("数学,选择题\n物理,填空题,题干")
        assertEquals(1, outcome.rows.size)
        assertEquals(1, outcome.errors.size)
        assertTrue(outcome.errors[0].contains("第 1 行"))
    }

    @Test
    fun empty_subject_or_content_is_rejected() {
        val outcome = MistakeTextParser.parse(",选择题,题干\n数学,填空题,")
        assertEquals(0, outcome.rows.size)
        assertEquals(2, outcome.errors.size)
    }

    @Test
    fun quoted_field_can_contain_comma() {
        val outcome = MistakeTextParser.parse(
            "数学,选择题,\"已知 a,b 均为正数，求 a+b\",C,A"
        )
        assertEquals(1, outcome.rows.size)
        assertEquals("已知 a,b 均为正数，求 a+b", outcome.rows[0].content)
    }

    @Test
    fun type_aliases_are_normalized() {
        assertEquals("选择题", MistakeTextParser.normalizeType("选择"))
        assertEquals("选择题", MistakeTextParser.normalizeType("单选题"))
        assertEquals("填空题", MistakeTextParser.normalizeType("填空"))
        assertEquals("解答题", MistakeTextParser.normalizeType("计算题"))
        // 认不出来的保留原文，不能丢
        assertEquals("判断题", MistakeTextParser.normalizeType("判断题"))
    }

    @Test
    fun empty_type_defaults_to_choice() {
        assertEquals("选择题", MistakeTextParser.normalizeType(""))
    }

    @Test
    fun sample_is_parseable_without_errors() {
        // 示例是要给用户直接点「插入示例」然后导入的，
        // 它自己必须先能通过校验
        val outcome = MistakeTextParser.parse(MistakeTextParser.SAMPLE)
        assertTrue("示例解析出错了: ${outcome.errors}", outcome.errors.isEmpty())
        assertTrue(outcome.rows.size >= 5)
        // 示例必须覆盖三种题型
        assertTrue(outcome.countByType.containsKey("选择题"))
        assertTrue(outcome.countByType.containsKey("填空题"))
        assertTrue(outcome.countByType.containsKey("解答题"))
    }
}