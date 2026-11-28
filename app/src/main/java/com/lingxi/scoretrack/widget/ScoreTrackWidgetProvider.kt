package com.lingxi.scoretrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.lingxi.scoretrack.MainActivity
import com.lingxi.scoretrack.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 桌面小组件（RemoteViews 实现）。
 *
 * ## 为什么从 Glance 换回来
 * Glance 是一条「Compose → 翻译成 RemoteViews」的链路，中间任何一环出问题
 * 都表现为**停在 initialLayout 不动**（之前在真机上就是一直转圈），而且
 * 没有异常冒到可见的地方 —— 因为组合是跑在 launcher 拉起的进程里。
 *
 * RemoteViews 是 launcher 的原生协议：要么 updateAppWidget 成功，要么当场
 * 抛异常（能抓日志）。排查成本天差地别，而这个小卡片本来也不需要 Compose
 * 的能力 —— 五个 TextView 而已。
 *
 * ## 线程
 * 广播有时间限制（约 10 秒），更新完不回来会被系统记一笔。
 * 所以用 goAsync() 把生命周期撑住，查库丢 IO、写 RemoteViews 回主线程。
 */
class ScoreTrackWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    /** ↻ 按钮：把当前所有实例重画一遍 */
    private fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, ScoreTrackWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) refresh(context, manager, ids)
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = runCatching { WidgetDataLoader.from(context).loadOnce() }
                    .onFailure { Log.e(TAG, "load failed", it) }
                    .getOrNull()

                withContext(Dispatchers.Main) {
                    ids.forEach { id ->
                        runCatching { manager.updateAppWidget(id, buildViews(context, snapshot)) }
                            .onFailure { Log.e(TAG, "updateAppWidget failed id=$id", it) }
                    }
                }
                Log.d(TAG, "updated ${ids.size} widget(s), hasData=${snapshot != null}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, snapshot: WidgetSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_scoretrack)

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))

        if (snapshot == null) {
            // 读库失败也要有东西看，别留一张空白卡片
            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_encourage, "暂时读不到数据")
            views.setTextViewText(R.id.widget_exam, "点一下打开看看")
            views.setTextViewText(R.id.widget_goal, "")
            views.setTextViewText(R.id.widget_plan, "")
            return views
        }

        views.setTextViewText(
            R.id.widget_title,
            snapshot.profileName.ifBlank { context.getString(R.string.app_name) }
        )
        views.setTextViewText(R.id.widget_encourage, snapshot.encouragement)

        if (snapshot.hasExam) {
            val examLine = buildString {
                append(snapshot.examName ?: "最近一次考试")
                append(" · ")
                append(snapshot.scoreText)
                snapshot.rate?.let { append("（${percent(it)}）") }
            }
            views.setTextViewText(R.id.widget_exam, examLine)

            val goalLine = snapshot.targetTotal?.let { target ->
                val gap = target - snapshot.totalScore
                if (gap > 0f) {
                    "目标 ${format(target)} · 还差 ${format(gap)}"
                } else {
                    "目标 ${format(target)} · 已达成 ✓"
                }
            }.orEmpty()
            views.setTextViewText(R.id.widget_goal, goalLine)
        } else {
            views.setTextViewText(R.id.widget_exam, "还没有考试记录")
            views.setTextViewText(R.id.widget_goal, "")
        }

        views.setTextViewText(R.id.widget_plan, "下一步 · ${snapshot.plan}")
        return views
    }

    /** 整卡点击：直接进 App（不新建任务栈，避免点两下开出两个首页） */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScoreTrackWidgetProvider::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ScoreTrackWidget"

        /**
         * 数据变化后由 App 广播触发重画。
         *
         * 为什么不让 MainActivity 直接调刷新的方法：刷新要用 goAsync()，
         * 那个对象只能从广播入口拿。走广播反而更稳 —— 同一个入口，
         * 系统定时更新和 App 主动更新走同一条代码。
         */
        const val ACTION_REFRESH = "com.lingxi.scoretrack.action.REFRESH_WIDGET"

        private const val REQUEST_OPEN = 100
        private const val REQUEST_REFRESH = 101
    }
}