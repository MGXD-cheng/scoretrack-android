package com.lingxi.scoretrack

import android.app.Application
import com.lingxi.scoretrack.util.CameraCapture
import dagger.hilt.android.HiltAndroidApp

/**
 * 学迹 ScoreTrack 的 Application 入口。
 * 挂上 @HiltAndroidApp 后，Hilt 会在应用启动时生成 DI 容器。
 */
@HiltAndroidApp
class ScoreTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 成绩单照片是临时文件，识别完就没用了。启动时扫一遍，
        // 把超过一天的残留清掉 —— 既省空间，也少一份隐私负担。
        CameraCapture.clearOldCaptures(this, olderThanMillis = DAY_MILLIS)
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}