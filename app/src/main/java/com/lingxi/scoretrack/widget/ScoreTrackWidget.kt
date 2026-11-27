package com.lingxi.scoretrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lingxi.scoretrack.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面小组件：一句激励 + 目标差距 + 下一步做什么。
 *
 * ## 数据为什么在 provideContent 里订阅
 * Glance 的 `update()` 只重跑 `provideContent`，**不会**重跑 `provideGlance`。
 * 如果在外面 `val snapshot = loader.load()` 再传进 lambda，数据会被冻结在
 * 第一次渲染那一刻 —— 之后刷新按钮、数据变化全都无效，而且界面上完全
 * 看不出问题（只是永远显示旧数字）。所以这里用 Flow + collectAsState，
 * 在 composition 内部读。
 *
 * ## 为什么计划不用 AI 生成
 * 小组件要的是「展开桌面就看到」—— 必须秒出、断网也在、刷新不花钱。
 * 每次刷新都打一次模型，慢、可能失败、还要花用户的钱。AI 的部分放在
 * App 里的成绩反思和分析报告，各司其职。
 */
class ScoreTrackWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 先取数据，再进 composition。
        //
        // 不在 provideContent 里 collectAsState：Glance 的 composition 是给
        // RemoteViews 用的精简实现，`LaunchedEffect` 不被可靠执行，而
        // collectAsState 内部靠它收集 Flow —— 于是第一帧永远出不来，
        // 桌面上只剩 initialLayout 那个转圈。
        //
        // provideGlance 跑在主线程，查库必须切 IO，否则直接 ANR。
        val snapshot = runCatching {
            withContext(Dispatchers.IO) { WidgetDataLoader.from(context).loadOnce() }
        }.getOrNull()

        provideContent {
            GlanceTheme {
                if (snapshot == null) FallbackContent() else WidgetContent(snapshot)
            }
        }
    }

    companion object {
        val SMALL = DpSize(180.dp, 110.dp)
        val WIDE = DpSize(320.dp, 160.dp)
    }
}

class ScoreTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScoreTrackWidget()
}

/** 点右上角的 ↻ 时重新渲染一次（数据本身由 Flow 驱动，这里只是手动兜底） */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        ScoreTrackWidget().update(context, glanceId)
    }
}

@Composable
private fun FallbackContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = "暂时读不到数据",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            text = "点一下打开学迹",
            modifier = GlanceModifier.padding(top = 4.dp),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val wide = size.width >= 250.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(14.dp)
            // 整卡可点，直接进 App
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        // ---------- 标题行 ----------
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = if (snapshot.profileName.isBlank()) "学迹" else snapshot.profileName,
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "↻",
                modifier = GlanceModifier
                    .padding(4.dp)
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 15.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // ---------- 激励语 ----------
        Text(
            text = snapshot.encouragement,
            maxLines = if (wide) 2 else 3,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        // ---------- 目标分析 ----------
        if (snapshot.hasExam) {
            Text(
                text = buildString {
                    append(snapshot.examName ?: "最近一次考试")
                    append(" · ")
                    append(snapshot.scoreText)
                    snapshot.rate?.let { append("（${percent(it)}）") }
                },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
            snapshot.targetTotal?.let { target ->
                val gap = target - snapshot.totalScore
                Text(
                    text = if (gap > 0f) {
                        "目标 ${format(target)} · 还差 ${format(gap)}"
                    } else {
                        "目标 ${format(target)} · 已达成 ✓"
                    },
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // ---------- 接下来的计划（推到最底下） ----------
        Spacer(modifier = GlanceModifier.defaultWeight())

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "下一步",
                modifier = GlanceModifier.padding(end = 6.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = snapshot.plan,
                maxLines = if (wide) 2 else 3,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}