package com.lingxi.scoretrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 学迹 ScoreTrack 的 Application 入口。
 * 挂上 @HiltAndroidApp 后，Hilt 会在应用启动时生成 DI 容器。
 */
@HiltAndroidApp
class ScoreTrackApp : Application()