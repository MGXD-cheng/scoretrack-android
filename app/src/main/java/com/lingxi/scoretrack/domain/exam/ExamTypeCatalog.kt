package com.lingxi.scoretrack.domain.exam

import com.lingxi.scoretrack.data.repository.AppSettingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 考试类型目录。
 *
 * 默认类型是写死的常量（周测/月考/…），用户新增的类型存在 app_setting 的
 * 一个 JSON 数组里。两者拼在一起才是界面上看到的完整列表。
 *
 * ## 为什么不给 exam 表加一张 type 表
 *
 * 类型只是一个标签，没有 id、没有额外字段、不需要被别的表引用。
 * exam.type 存的本来就是字符串 —— 建成实体表之后，每次读考试列表都要 join，
 * 而收益只是「改一次类型名能全局生效」。用一个字符串数组更轻。
 *
 * ## 为什么不存进 SharedPreferences
 *
 * 项目已经有 app_setting 这张表了（而且它会跟着备份/同步一起走）。
 * 自定义类型是**用户数据**，不该在换手机或者从备份恢复之后凭空消失。
 */
@Singleton
class ExamTypeCatalog @Inject constructor(
    private val appSettingRepository: AppSettingRepository
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 用户自建的类型（不含默认的那些） */
    val customTypes: Flow<List<String>> =
        appSettingRepository.observe(KEY_CUSTOM_TYPES).map { decode(it) }

    /** 界面上可选的完整列表：默认在前，自建在后，去重 */
    val allTypes: Flow<List<String>> = customTypes.map { custom ->
        (DEFAULT_TYPES + custom).distinct()
    }

    /**
     * 新增一个自定义类型。
     *
     * @return 失败原因（直接可以拿去给用户看）；成功返回 null。
     */
    suspend fun add(rawName: String): String? {
        val name = rawName.trim()
        if (name.isEmpty()) return "类型名不能为空"
        if (name.length > 12) return "类型名太长了，12 个字以内"

        val current = decode(appSettingRepository.get(KEY_CUSTOM_TYPES))
        if ((DEFAULT_TYPES + current).any { it.equals(name, ignoreCase = true) }) {
            return "「$name」已经有了"
        }
        save(current + name)
        return null
    }

    /**
     * 删除一个自建类型。默认类型删不掉 —— 它们不在这份列表里，
     * 传进来也不会被删（不做静默失败，直接忽略）。
     */
    suspend fun remove(name: String) {
        val current = decode(appSettingRepository.get(KEY_CUSTOM_TYPES))
        if (name !in current) return
        save(current - name)
    }

    private suspend fun save(list: List<String>) {
        appSettingRepository.put(KEY_CUSTOM_TYPES, encode(list))
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    companion object {
        const val KEY_CUSTOM_TYPES = "exam_types_custom"

        /**
         * 默认类型。
         *
         * 「周测」是新加的，而且放在第一位 —— 它是学期中频率最高的考试，
         * 录入时十有八九就是它，放最前面能少点一次。
         */
        val DEFAULT_TYPES: List<String> = listOf(
            "周测",
            "月考",
            "期中",
            "期末",
            "模拟",
            "随堂",
            "其他"
        )
    }
}