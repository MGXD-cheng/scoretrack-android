package com.lingxi.scoretrack.domain.sync

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 快照里的设置合并规则。
 *
 * ## 为什么单独抽出来
 * 这段逻辑决定「恢复备份之后，你自建的考试类型还在不在」。它不碰数据库、
 * 不碰协程，纯字符串进、纯字符串出 —— 所以能被单元测试按住。把这样的规则
 * 埋在 SnapshotImporter 的事务里，就只能靠手点来验证了，而手点的人
 * （包括我）不会每次都真的去点。
 *
 * ## 两类数据，两种丢法
 * - **累积型**（自定义考试类型）：直接覆盖会把本机独有的那几条抹掉，
 *   用户会莫名其妙少几个类型。取并集。
 * - **开关型**（自动归纳）：没有「累积」语义，以传入的快照为准。
 *
 * 解析失败一律当空处理：值格式不对只该让这一个设置项失效，
 * 不该让整次导入回滚 —— 成绩数据比几个考试类型重要得多。
 */
object SettingMerger {

    private val json = Json { ignoreUnknownKeys = true }

    /** 解码 JSON 字符串数组。任何异常都退化成空列表。 */
    fun decodeList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun encodeList(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    /**
     * 把两边的字符串列表取并集，本地顺序在前、远端新增的追加在后。
     *
     * 去重忽略大小写：「单元测」和「单元測」算两条（繁简不同），
     * 但「Unit」和「unit」算一条 —— 后者纯属手滑，不该在界面上并列出现。
     */
    fun mergeLists(local: List<String>, remote: List<String>): List<String> =
        (local + remote).distinctBy { it.lowercase() }

    /**
     * 合并两个「JSON 数组字符串」形式的设置值。
     *
     * @return 合并后的 JSON 字符串。两边都是空时返回空列表的字面量 `[]`，
     *         而不是空字符串 —— 后者会让下次解析走进「不是 JSON」的分支。
     */
    fun mergeEncoded(localRaw: String?, remoteRaw: String?): String =
        encodeList(mergeLists(decodeList(localRaw), decodeList(remoteRaw)))
}
