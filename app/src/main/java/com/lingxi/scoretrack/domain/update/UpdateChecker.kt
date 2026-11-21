package com.lingxi.scoretrack.domain.update

import com.lingxi.scoretrack.data.remote.GitHubApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次更新检查的结果。
 *
 * [error] 非空时表示检查本身失败了（断网、404、限流），此时
 * [hasUpdate] 恒为 false —— 界面上要区分「没新版本」和「没查成功」，
 * 这两件事对用户的含义完全不同。
 */
data class UpdateResult(
    val hasUpdate: Boolean = false,
    val currentVersion: String = "",
    val latestVersion: String = "",
    val releaseUrl: String = "",
    val apkUrl: String = "",
    val notes: String = "",
    val error: String? = null
)

/**
 * 从 GitHub Releases 检查新版本。
 *
 * ## 为什么不做静默自动检查
 *
 * 这是个本地记账/成绩类应用，用户可能一年都不需要更新一次。
 * 每次启动都去请求一次 GitHub，既费流量也费电，而且在没有网络的环境下
 * 还会在日志里堆一堆失败记录。**由用户主动点一下**，代价是一次点击，
 * 收益是应用永远不会在后台偷偷联网。
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val gitHubApi: GitHubApi
) {

    suspend fun check(currentVersion: String): UpdateResult = runCatching {
        val dto = gitHubApi.latestRelease(LATEST_RELEASE_URL)

        if (dto.draft || dto.prerelease) {
            // 预发布版本不该推给普通用户
            return@runCatching UpdateResult(
                currentVersion = currentVersion,
                latestVersion = currentVersion,
                error = null
            )
        }

        val latest = dto.tagName.ifBlank { dto.name }
        val apk = dto.assets.firstOrNull { it.isApk }

        UpdateResult(
            hasUpdate = isNewer(latest, currentVersion),
            currentVersion = currentVersion,
            latestVersion = latest.trim().removePrefix("v").removePrefix("V"),
            releaseUrl = dto.htmlUrl,
            apkUrl = apk?.downloadUrl.orEmpty(),
            // Release 说明可能上千字，列表里只给个开头，点进去看全文
            notes = dto.body.trim().take(600)
        )
    }.getOrElse { throwable ->
        UpdateResult(
            currentVersion = currentVersion,
            error = when (throwable) {
                is IOException -> "连不上 GitHub，检查一下网络再试"
                else -> throwable.message?.takeIf { it.isNotBlank() }
                    ?: "检查更新失败，稍后再试"
            }
        )
    }

    /**
     * 版本号比较。
     *
     * 按点分段、逐段比数字：`0.10.0` 比 `0.9.9` 新（字符串比较会得出相反的结论，
     * 这是版本号最容易踩的坑）。非数字段（`-beta`、`+build`）直接忽略。
     */
    fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = parseVersion(remote)
        val currentParts = parseVersion(current)
        val size = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until size) {
            val a = remoteParts.getOrElse(index) { 0 }
            val b = currentParts.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(".", "-", "+", "_")
            .mapNotNull { it.toIntOrNull() }

    companion object {
        const val OWNER = "MGXD-cheng"
        const val REPO = "scoretrack-android"

        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

        /** 浏览器里打开的仓库页 —— 没有 Release 时给用户一个兜底入口 */
        const val REPO_URL = "https://github.com/$OWNER/$REPO"
    }
}