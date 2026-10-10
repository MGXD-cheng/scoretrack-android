package com.lingxi.scoretrack.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 轻量自定义图标。
 *
 * 为什么不直接用 material-icons-extended？—— 那个库带 5000+ 图标，
 * debug 构建不做裁剪时会全量打成 dex，构建时间与包体都不划算。
 * 缺口用几个手写 ImageVector 补上即可。
 */
val ChartBarIcon: ImageVector = ImageVector.Builder(
    name = "ChartBar",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        // 左柱
        moveTo(4f, 20f)
        lineTo(8f, 20f)
        lineTo(8f, 11f)
        lineTo(4f, 11f)
        close()
        // 中柱
        moveTo(10f, 20f)
        lineTo(14f, 20f)
        lineTo(14f, 4f)
        lineTo(10f, 4f)
        close()
        // 右柱
        moveTo(16f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 14f)
        lineTo(16f, 14f)
        close()
    }
}.build()