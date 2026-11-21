package com.lingxi.scoretrack.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /repos/{owner}/{repo}/releases/latest` 的响应。
 *
 * 字段全部给了默认值：GitHub 的这个接口偶尔会少给字段（比如没有 assets），
 * 少一个字段不该让整个更新检查崩掉。
 */
@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String = "",
    /** Release 说明（Markdown）。可能很长，展示时做截断 */
    val body: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("published_at")
    val publishedAt: String = "",
    val draft: Boolean = false,
    @SerialName("prerelease")
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url")
    val downloadUrl: String = "",
    val size: Long = 0L
) {
    /** 只认 APK —— 别的资源（源码 zip、混淆映射）不是给普通用户下载的 */
    val isApk: Boolean get() = name.endsWith(".apk", ignoreCase = true)
}