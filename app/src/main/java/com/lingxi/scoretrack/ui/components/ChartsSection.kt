package com.lingxi.scoretrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingxi.scoretrack.ui.screen.charts.ChartsUiState
import com.lingxi.scoretrack.ui.util.formatDateTime
import com.lingxi.scoretrack.ui.util.formatScore

/**
 * 图表区块 —— 首页和「图表」共用同一份实现。
 *
 * 提到 components 里是因为图表不再单独占一个 Tab：**它本来就是首页的主体内容**，
 * 单独一个入口只会让首页显得空、图表页显得重复。
 *
 * ⚠️ 这里只能用 Column，**绝不能用 LazyColumn** —— 它会被塞进首页的
 * LazyColumn 里，Lazy 嵌 Lazy 会直接抛异常。
 */
@Composable
fun ChartsSection(
    state: ChartsUiState,
    onOpenGoalEdit: () -> Unit,
    onOpenAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChartCard(
            title = "总分趋势",
            subtitle = "按考试时间排序，纵轴为得分率 %"
        ) {
            ScoreTrendLineChart(
                points = state.trendPoints,
                lineColor = colorScheme.primary,
                labelColor = colorScheme.onSurfaceVariant,
                gridColor = colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }

        ChartCard(
            title = "科目均衡度",
            subtitle = if (state.latestExamName.isNotEmpty()) {
                "最近一次：${state.latestExamName}"
            } else {
                "最近一次考试"
            }
        ) {
            SubjectRadarChart(
                points = state.latestRatePoints,
                accentColor = colorScheme.primary,
                labelColor = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }

        GoalCard(state = state, onOpenGoalEdit = onOpenGoalEdit)
        AnalysisCard(state = state, onOpenAnalysis = onOpenAnalysis)
    }
}

/**
 * 考试目标卡片。
 *
 * 有目标就显示进度条；没目标时它是一张「邀请卡」，不是报错提示 ——
 * 用户第一次进来看到的应该是「可以做什么」，而不是「你缺什么」。
 */
@Composable
fun GoalCard(
    state: ChartsUiState,
    onOpenGoalEdit: () -> Unit
) {
    val goal = state.totalGoal
    val progress = state.goalProgress

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "考试目标",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenGoalEdit) {
                    Text(if (goal == null) "去设定" else "调整")
                }
            }

            if (goal == null) {
                Text(
                    text = "给下一次考试定个目标分，进度会画在这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "目标 ${formatScore(goal.targetScore)} 分" +
                    (goal.targetRank?.let { " · 名次进前 $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("最近一次 ")
                        append(formatScore(state.latestTotalScore))
                        append(" 分 · 达成 ")
                        append((progress * 100).toInt())
                        append("%")
                        state.goalGap?.let {
                            append(" · 还差 ")
                            append(formatScore(it))
                            append(" 分")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress >= 1f) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "录入一次考试后，这里会显示离目标还有多远。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.subjectGoals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                state.subjectGoals.forEach { (subject, subjectGoal) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${formatScore(subjectGoal.targetScore)} 分",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * AI 分析卡片。
 *
 * 直接把 Markdown 全文渲染出来 —— 用户滑到底就能读完，
 * 不用再点进列表里翻。想看历史报告才进「查看全部」。
 */
@Composable
fun AnalysisCard(
    state: ChartsUiState,
    onOpenAnalysis: () -> Unit
) {
    val report = state.latestReport

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI 分析",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenAnalysis) {
                    Text("查看全部")
                }
            }

            if (report == null) {
                Text(
                    text = "还没有分析报告。点「查看全部」进去生成一份，" +
                        "AI 会读一遍你的成绩、目标和错题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenAnalysis,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("生成分析报告")
                }
            } else {
                Text(
                    text = "生成于 ${formatDateTime(report.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 直接渲染全文，不做截断
                MarkdownText(markdown = report.content)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenAnalysis,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("历史报告")
                }
            }
        }
    }
}

@Composable
fun ChartCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}