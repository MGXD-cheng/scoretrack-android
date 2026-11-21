package com.lingxi.scoretrack.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置合并规则。
 *
 * 这几个用例存在的理由只有一个：**证明恢复备份不会弄丢用户自己攒的东西**。
 * 上一版快照根本没带 app_setting，自建的考试类型换手机就没了 —— 而这种
 * 丢失在界面上完全看不出来，用户只会觉得自己记错了。
 */
class SettingMergerTest {

    @Test
    fun `解码非法内容返回空列表而不是抛异常`() {
        assertEquals(emptyList<String>(), SettingMerger.decodeList(null))
        assertEquals(emptyList<String>(), SettingMerger.decodeList(""))
        assertEquals(emptyList<String>(), SettingMerger.decodeList("   "))
        assertEquals(emptyList<String>(), SettingMerger.decodeList("不是JSON"))
        assertEquals(emptyList<String>(), SettingMerger.decodeList("[1,2,3]"))
    }

    @Test
    fun `编码再解码能原样回来`() {
        val list = listOf("单元测", "周练", "联考")
        val encoded = SettingMerger.encodeList(list)
        assertEquals(list, SettingMerger.decodeList(encoded))
    }

    @Test
    fun `并集保留本地顺序，远端新增的追加在后面`() {
        val merged = SettingMerger.mergeLists(
            local = listOf("单元测", "周练"),
            remote = listOf("联考", "单元测")
        )
        assertEquals(listOf("单元测", "周练", "联考"), merged)
    }

    @Test
    fun `本机独有的类型不会因为同步而消失`() {
        // 这就是当初那个 bug 的回归用例：本机有「单元测」，远端只有「联考」，
        // 直接覆盖的话「单元测」就没了
        val merged = SettingMerger.mergeLists(
            local = listOf("单元测"),
            remote = listOf("联考")
        )
        assertTrue("单元测" in merged)
        assertTrue("联考" in merged)
    }

    @Test
    fun `去重忽略大小写`() {
        val merged = SettingMerger.mergeLists(
            local = listOf("Unit Test"),
            remote = listOf("unit test", "UNIT TEST")
        )
        assertEquals(listOf("Unit Test"), merged)
    }

    @Test
    fun `两边都为空时结果是合法的空数组字面量`() {
        val merged = SettingMerger.mergeEncoded(null, null)
        assertEquals("[]", merged)
        // 关键：必须能被再次解码，否则下次合并会走进「不是 JSON」的分支
        assertEquals(emptyList<String>(), SettingMerger.decodeList(merged))
    }

    @Test
    fun `一侧损坏时另一侧仍然被保住`() {
        // 远端快照来自老版本、格式不对：本机数据不能跟着陪葬
        val merged = SettingMerger.mergeEncoded("[\"单元测\"]", "{{坏掉的}}")
        assertEquals(listOf("单元测"), SettingMerger.decodeList(merged))
    }
}