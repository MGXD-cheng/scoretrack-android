package com.lingxi.scoretrack.domain.update

import com.lingxi.scoretrack.data.remote.GitHubApi
import com.lingxi.scoretrack.data.remote.dto.GitHubReleaseDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较的单元测试（纯 JVM）。
 *
 * 这块逻辑一旦写错，用户要么永远收不到更新提示，要么每次打开都说「有新版本」——
 * 而且两种错都不会崩，只会安静地错着。所以要测。
 */
class UpdateCheckerTest {

    /** isNewer 不碰网络，但构造函数要一个 API 实例 */
    private class FakeApi : GitHubApi {
        override suspend fun latestRelease(url: String) = GitHubReleaseDto()
    }

    private val checker = UpdateChecker(FakeApi())

    @Test
    fun patch_bump_is_newer() {
        assertTrue(checker.isNewer("0.2.1", "0.2.0"))
    }

    @Test
    fun two_digit_minor_is_newer_than_one_digit() {
        // 字符串比较会认为 "0.10.0" < "0.9.9"，这是版本号最经典的坑
        assertTrue(checker.isNewer("0.10.0", "0.9.9"))
        assertFalse(checker.isNewer("0.9.9", "0.10.0"))
    }

    @Test
    fun same_version_is_not_newer() {
        assertFalse(checker.isNewer("0.3.0", "0.3.0"))
    }

    @Test
    fun older_remote_is_not_newer() {
        assertFalse(checker.isNewer("0.2.0", "0.3.0"))
    }

    @Test
    fun v_prefix_is_ignored() {
        assertTrue(checker.isNewer("v0.3.1", "0.3.0"))
        assertFalse(checker.isNewer("v0.3.0", "0.3.0"))
    }

    @Test
    fun prerelease_suffix_does_not_beat_release() {
        // 0.3.0-beta 和 0.3.0 视为同一版（非数字段被忽略），不推更新
        assertFalse(checker.isNewer("0.3.0-beta", "0.3.0"))
    }

    @Test
    fun missing_segments_count_as_zero() {
        assertTrue(checker.isNewer("0.3.1", "0.3"))
        assertFalse(checker.isNewer("0.3", "0.3.0"))
    }
}