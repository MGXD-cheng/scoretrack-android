package com.lingxi.scoretrack.data.remote

import com.lingxi.scoretrack.data.remote.dto.ChatRequestDto
import com.lingxi.scoretrack.data.remote.dto.ChatResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI 兼容的 Chat Completions 接口。
 * 用 @Url 传完整地址，便于在 DeepSeek / 其他兼容服务间自由切换。
 */
interface AiApi {

    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: ChatRequestDto
    ): ChatResponseDto
}