package com.lingxi.scoretrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet
import com.lingxi.scoretrack.ui.screen.charts.LossSlice
import com.lingxi.scoretrack.ui.screen.charts.RatePoint
import com.lingxi.scoretrack.ui.screen.charts.TrendPoint

/**
 * MPAndroidChart 是 View 体系的库，这里用 AndroidView 桥接进 Compose。
 * 图表配色从 MaterialTheme 取，保证跟整体主题一致。
 */

@Composable
fun ScoreTrendLineChart(
    points: List<TrendPoint>,
    lineColor: Color,
    labelColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setNoDataText("还没有考试数据")
                setNoDataTextColor(labelColor.toArgbCompat())
                axisRight.isEnabled = false
                axisLeft.apply {
                    setDrawGridLines(true)
                    // 注意：必须用 this. 限定 —— 否则会被同名参数 gridColor: Color 遮蔽
                    this.gridColor = gridColor.toArgbCompat()
                    axisMinimum = 0f
                    axisMaximum = 100f
                    textColor = labelColor.toArgbCompat()
                    setLabelCount(5, true)
                }
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    textColor = labelColor.toArgbCompat()
                    labelRotationAngle = -20f
                }
            }
        },
        update = { chart ->
            val entries = points.mapIndexed { index, point ->
                Entry(index.toFloat(), point.rate * 100f)
            }
            val dataSet = LineDataSet(entries, "得分率").apply {
                color = lineColor.toArgbCompat()
                setCircleColor(lineColor.toArgbCompat())
                lineWidth = 2.5f
                circleRadius = 4f
                setDrawCircleHole(false)
                setDrawValues(true)
                valueTextSize = 10f
                valueTextColor = labelColor.toArgbCompat()
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = lineColor.toArgbCompat()
                fillAlpha = 40
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        "%.0f".format(value)
                }
            }
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(points.map { it.label })
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}

@Composable
fun SubjectRadarChart(
    points: List<RatePoint>,
    accentColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    /** 目标线。为空表示这个档案还没设过任何科目目标，那就只画一条线 */
    targetPoints: List<RatePoint> = emptyList(),
    targetColor: Color = accentColor
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RadarChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)
                setNoDataText("还没有考试数据")
                setNoDataTextColor(labelColor.toArgbCompat())
                webLineWidth = 1f
                webLineWidthInner = 1f
                webColor = labelColor.toArgbCompat()
                webColorInner = labelColor.toArgbCompat()
                webAlpha = 60
                yAxis.apply {
                    setDrawLabels(false)
                    axisMinimum = 0f
                    axisMaximum = 100f
                }
                xAxis.apply {
                    textColor = labelColor.toArgbCompat()
                    textSize = 11f
                    setDrawLabels(true)
                    yOffset = 12f
                }
                legend.apply {
                    textColor = labelColor.toArgbCompat()
                    textSize = 11f
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    formSize = 9f
                    xEntrySpace = 16f
                    yOffset = 6f
                }
            }
        },
        update = { chart ->
            // 只有一条线的时候图例是废话 —— 它只会重复标题
            chart.legend.isEnabled = targetPoints.isNotEmpty()

            val dataSets = mutableListOf<IRadarDataSet>()

            val entries = points.map { RadarEntry(it.rate * 100f) }
            dataSets += RadarDataSet(entries, "实际").apply {
                color = accentColor.toArgbCompat()
                fillColor = accentColor.toArgbCompat()
                setDrawFilled(true)
                fillAlpha = 70
                lineWidth = 2f
                setDrawValues(false)
            }

            if (targetPoints.isNotEmpty()) {
                val targetEntries = targetPoints.map { RadarEntry(it.rate * 100f) }
                dataSets += RadarDataSet(targetEntries, "目标").apply {
                    color = targetColor.toArgbCompat()
                    // 目标线**不填充**：雷达图上两层半透明填充会互相压色，
                    // 而目标本来就只是个参照系，轮廓比面积更说明问题
                    setDrawFilled(false)
                    lineWidth = 2.5f
                    setDrawValues(false)
                }
            }

            chart.xAxis.valueFormatter = IndexAxisValueFormatter(points.map { it.subjectName })
            chart.data = RadarData(dataSets)
            chart.invalidate()
        }
    )
}

/** Compose Color → Android 原生 ARGB int */
private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)

/**
 * 失分构成饼图。
 *
 * 用**警示色系**而不是主题色 —— 这张图回答的是「分丢在哪」，
 * 一片绿色会让人误以为「这科很好」，哪怕它的占比最大。
 */
@Composable
fun SubjectLossPieChart(
    slices: List<LossSlice>,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                setNoDataText("还没有可绘制的失分数据")
                setNoDataTextColor(labelColor.toArgbCompat())
                setUsePercentValues(true)
                setDrawEntryLabels(false)      // 科目名放进图例，不挤在扇形上
                isDrawHoleEnabled = true
                holeRadius = 52f
                transparentCircleRadius = 56f
                setDrawCenterText(true)
                setCenterTextSize(12f)
                setCenterTextColor(labelColor.toArgbCompat())
                legend.apply {
                    textColor = labelColor.toArgbCompat()
                    textSize = 11f
                    isWordWrapEnabled = true
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }
                setTouchEnabled(true)
                // 拖动/缩放对饼图没意义，误触反而容易转飞
                isRotationEnabled = false
            }
        },
        update = { chart ->
            val totalLoss = slices.sumOf { it.loss.toDouble() }.toFloat()
            chart.centerText = if (totalLoss > 0f) {
                "总失分\n${"%.0f".format(totalLoss)}"
            } else {
                ""
            }

            val entries = slices.map { PieEntry(it.loss, it.subjectName) }
            val dataSet = PieDataSet(entries, "").apply {
                colors = LOSS_PALETTE.toList()
                sliceSpace = 2f
                setDrawValues(true)
                valueTextSize = 11f
                valueTextColor = android.graphics.Color.WHITE
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        // 占比太小的时候文字叠在一起反而看不清，直接留白
                        if (value < 6f) "" else "%.0f%%".format(value)
                }
            }
            chart.data = PieData(dataSet)
            chart.invalidate()
        }
    )
}

/**
 * 失分专用色板。
 *
 * 刻意排除绿色（那是「做得好」的颜色），从红开始往冷色走，
 * 让「失分最多的那科」在视觉上最重。
 */
private val LOSS_PALETTE = intArrayOf(
    0xFFEF4444.toInt(), // 红
    0xFFF97316.toInt(), // 橙
    0xFFF59E0B.toInt(), // 琥珀
    0xFFEC4899.toInt(), // 粉
    0xFFA855F7.toInt(), // 紫
    0xFF6366F1.toInt(), // 靛
    0xFF14B8A6.toInt(), // 青
    0xFF84CC16.toInt()  // 黄绿（收尾，占比通常最小）
)