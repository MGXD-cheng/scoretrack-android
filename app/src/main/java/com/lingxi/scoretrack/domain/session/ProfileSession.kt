package com.lingxi.scoretrack.domain.session

import com.lingxi.scoretrack.data.repository.AppSettingRepository
import com.lingxi.scoretrack.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局「当前档案」会话。
 *
 * ## 为什么需要它
 *
 * 之前每个 ViewModel 各自持有 `private val profileId = MutableStateFlow(0L)`，
 * 在 init 里读一次 `app_setting.current_profile_id` 就再也不管了。
 * 全项目有 10 个 ViewModel 这么做 —— 于是切换档案时：
 *
 * - 谁切谁更新（HomeViewModel 自己改自己的 flow）
 * - 其他 9 个完全不知道，继续拿着旧 id 查数据
 * - 用户看到的就是「切换了但页面没变」
 *
 * 修法不是给每个 ViewModel 加一个 `refresh()` 手动调用（那样会漏、且顺序难保证），
 * 而是把「当前档案」提升成**唯一数据源**：所有 ViewModel 订阅同一个
 * [currentProfileId]，切换时只改这一处，全体自动重算。
 *
 * ## 顺带解决的
 *
 * - 进程被系统杀掉重建后，会自动校验存下来的 id 是否还存在（档案可能被删了），
 *   不存在就回退到默认档案，而不是拿着一个悬空 id 查出一堆空列表。
 * - 持久化只在这里做一次，不再散落在 10 个地方。
 */
@Singleton
class ProfileSession @Inject constructor(
    private val appSettingRepository: AppSettingRepository,
    private val profileRepository: ProfileRepository
) {

    private val _currentProfileId = MutableStateFlow(0L)

    /** 当前档案 id。0 表示「还没有任何档案」或「正在加载」。 */
    val currentProfileId: StateFlow<Long> = _currentProfileId.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { bootstrap() }
    }

    /**
     * 启动时从设置表恢复上次用的档案。
     *
     * 注意 [com.lingxi.scoretrack.data.local.entity.ProfileEntity.isDefault] 的语义：
     * 它实际上被当成了「上次用的 / 启动时打开的那个」，而不只是「默认档案」。
     * 这是历史包袱，暂时沿用 —— 改语义会影响同步与删除逻辑。
     */
    private suspend fun bootstrap() {
        val stored = appSettingRepository.getLong(
            AppSettingRepository.KEY_CURRENT_PROFILE_ID,
            0L
        )
        // 档案可能已经被删了，存下来的 id 不一定还有效
        val stillExists = stored > 0L && profileRepository.getById(stored) != null
        val resolved = if (stillExists) {
            stored
        } else {
            profileRepository.getDefault()?.id
                ?: profileRepository.getAllOnce().firstOrNull()?.id
                ?: 0L
        }
        _currentProfileId.value = resolved
        if (resolved > 0L && resolved != stored) {
            appSettingRepository.putLong(
                AppSettingRepository.KEY_CURRENT_PROFILE_ID,
                resolved
            )
        }
    }

    /**
     * 切换当前档案。
     *
     * **所有 ViewModel 立刻感知** —— 它们订阅的是同一个 StateFlow，
     * `flatMapLatest` 会取消旧查询、用新 id 重新订阅数据库。
     * 这就是「切换后自动刷新」。
     */
    suspend fun switchTo(profileId: Long) {
        if (profileId <= 0L) return
        if (_currentProfileId.value == profileId) return

        _currentProfileId.value = profileId
        appSettingRepository.putLong(
            AppSettingRepository.KEY_CURRENT_PROFILE_ID,
            profileId
        )
        // 保持「当前档案 == 默认档案」这一既有语义，见 bootstrap 的注释
        val profile = profileRepository.getById(profileId)
        if (profile != null && !profile.isDefault) {
            profileRepository.setDefault(profileId)
        }
    }

    /** 外部改了默认档案（例如删除了当前档案）后，把会话同步过去。 */
    suspend fun syncFromStorage() {
        bootstrap()
    }
}
