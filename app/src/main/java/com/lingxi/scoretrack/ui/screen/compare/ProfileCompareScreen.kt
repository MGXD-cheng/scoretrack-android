package com.lingxi.scoretrack.ui.screen.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingxi.scoretrack.ui.components.AppTopBar
import com.lingxi.scoretrack.ui.util.formatDate
import kotlin.math.roundToInt

/**
 * 多档案数据对比。
 *
 * 只展示「可比的数字」——得分率、考试次数、错题量。
 * 不做「总分对比」这种看着直观其实没意义的东西：
 * 一次满分 750、一次满分 600 的两次考试，总分放一起比是误导。
 */
@Composable
fun ProfileCompareScreen(
    onBack: () -> Unit,
    viewModel: ProfileCompareViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "档案对比", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isEmpty) {
                item {
                    Text(
                        text = "还没有档案可以对比。到设置里建两个档案，各自的成绩录进去之后这里就有内容了。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        text = if (state.hasAnyScore) {
                            "按「平均得分率」从高到低排。得分率 = 每次考试拿到的分 ÷ 满分，再取平均 —— 满分不同的考试也能放一起比。"
                        } else {
                            "还没有录入成绩，先录一场考试再来对比。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                items(state.rows, key = { it.profileId }) { row ->
                    SummaryCard(
                        summary = row,
                        isLeader = row.profileId == state.bestProfileId && state.hasAnyScore
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(summary: ProfileSummary, isLeader: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLeader) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLeader) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isLeader) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = "领先",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = summary.averageRate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isLeader) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text(
                    text = "平均得分率",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            RateBar(rate = summary.averageRate)

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem("考试", "${summary.examCount} 场", Modifier.weight(1f))
                MetricItem("科目", "${summary.subjectCount} 门", Modifier.weight(1f))
                MetricItem("错题", "${summary.mistakeCount} 道", Modifier.weight(1f))
                MetricItem("待复习", "${summary.pendingMistakes} 道", Modifier.weight(1f))
            }

            if (summary.latestExamName != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val total = summary.latestTotal
                val full = summary.latestTotalFull
                Text(
                    text = buildString {
                        append("最近一次：")
                        append(summary.latestExamName)
                        summary.latestExamDate?.let {
                            append(" · ")
                            append(formatDate(it))
                        }
                        if (total != null && full != null) {
                            append(" · ")
                            append(formatScoreValue(total))
                            append(" / ")
                            append(formatScoreValue(full))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RateBar(rate: Float?) {
    val clamped = (rate ?: 0f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (rate != null && clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatScoreValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)