package com.lingxi.scoretrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
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
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RadarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
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
            }
        },
        update = { chart ->
            val entries = points.map { RadarEntry(it.rate * 100f) }
            val dataSet = RadarDataSet(entries, "得分率").apply {
                color = accentColor.toArgbCompat()
                fillColor = accentColor.toArgbCompat()
                setDrawFilled(true)
                fillAlpha = 70
                lineWidth = 2f
                setDrawValues(false)
            }
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(points.map { it.subjectName })
            chart.data = RadarData(dataSet)
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