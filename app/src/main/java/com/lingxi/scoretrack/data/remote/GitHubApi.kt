package com.lingxi.scoretrack.data.remote

import com.lingxi.scoretrack.data.remote.dto.GitHubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * GitHub Releases API。
 *
 * 用 `@Url` 传完整地址而不是吃 baseUrl —— 项目的 Retrofit 默认指向 AI 服务商，
 * 而且用户可以随时改 AI 地址。更新检查不该跟着 AI 配置走。
 */
interface GitHubApi {

    @GET
    suspend fun latestRelease(@Url url: String): GitHubReleaseDto
}