package com.lingxi.scoretrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingxi.scoretrack.ui.screen.charts.HeatRow
import com.lingxi.scoretrack.ui.util.scoreColorHex

/**
 * 科目 × 考试 的得分率热力图。
 *
 * MPAndroidChart 没有热力图组件，所以这里用 Compose 原生绘制 ——
 * 反正它就是一排带底色的小方块，用 Canvas 反而是杀鸡用牛刀。
 *
 * 这张图回答的是折线图回答不了的问题：**哪一科是长期短板**。
 * 总分趋势看不出「数学一直拖后腿」，但矩阵里那一行如果整片偏红，
 * 一眼就藏不住了。
 *
 * 格子的 null 与 0 分**必须区分**：前者是「这次没考这科」（灰格子），
 * 后者是「考了但一分没得」（红格子）。混作一谈会冤枉人。
 */
@Composable
fun SubjectExamHeatMap(
    rows: List<HeatRow>,
    examLabels: List<String>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty() || examLabels.isEmpty()) return

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val subjectColumnWidth = 52.dp

    Column(modifier = modifier.fillMaxWidth()) {

        // ---- 表头：科目列留白 + 各次考试名 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Spacer(modifier = Modifier.width(subjectColumnWidth))
            examLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ---- 矩阵本体 ----
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.subjectName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(subjectColumnWidth)
                )

                row.rates.forEach { rate ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellColor(rate)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rate != null) {
                            Text(
                                text = "${(rate * 100).toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = cellContentColor(rate)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HeatLegend()
    }
}

/**
 * 格子底色。
 *
 * null 用 surfaceVariant —— 刻意做成「空」的样子，而不是随便选个
 * 中性色，这样「没数据」和「低分」在视觉上完全不会混淆。
 */
@Composable
private fun cellColor(rate: Float?): Color {
    if (rate == null) return MaterialTheme.colorScheme.surfaceVariant
    // 半透明填充，让密集的矩阵不至于像马赛克一样刺眼
    return Color(scoreColorHex(rate)).copy(alpha = 0.85f)
}

/**
 * 格子里的文字颜色。
 *
 * 用亮度判断而不是按档位硬编码：琥珀色(#F59E0B)上白字几乎看不清，
 * 而深红色上黑字同样糟糕。让背景自己决定。
 */
private fun cellContentColor(rate: Float): Color {
    val background = Color(scoreColorHex(rate))
    return if (background.luminance() > 0.5f) Color(0xFF1A1D26) else Color.White
}

/** 图例：把颜色和档位对上，否则用户只能猜 */
@Composable
private fun HeatLegend() {
    val items = listOf(
        0.9f to "优秀",
        0.75f to "良好",
        0.62f to "及格",
        0.4f to "待提升"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (rate, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(scoreColorHex(rate)).copy(alpha = 0.85f))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}