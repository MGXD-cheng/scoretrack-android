package com.lingxi.scoretrack.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room 类型转换器。
 * 目前用于把知识点别名（List<String>）以 JSON 数组形式落库。
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        json.encodeToString(stringListSerializer, value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(stringListSerializer, value) }
                .getOrDefault(emptyList())
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val stringListSerializer = ListSerializer(String.serializer())
    }
}