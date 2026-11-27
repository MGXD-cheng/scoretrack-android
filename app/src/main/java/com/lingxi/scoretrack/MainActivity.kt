package com.lingxi.scoretrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.lingxi.scoretrack.ui.navigation.AppNavHost
import com.lingxi.scoretrack.ui.theme.ScoreTrackTheme
import com.lingxi.scoretrack.widget.ScoreTrackWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 单 Activity 架构，全部页面由 Compose 承载。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }

    /**
     * 每次回到前台顺手刷一次桌面小组件。
     *
     * 不放在各个 ViewModel 的保存回调里：那样要在一个个地方记得调，
     * 迟早会漏。onResume 这一处覆盖了「用户刚录完成绩 / 改完目标 / 切换档案」
     * 的全部场景，代价只是多渲染一次 RemoteViews。
     *
     * 失败不弹任何东西 —— 小组件刷新失败不该打断用户手上的事。
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { ScoreTrackWidget().updateAll(this@MainActivity) }
        }
    }
}
