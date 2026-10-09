package com.lingxi.scoretrack.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角规范：卡片 16dp，按钮 12dp，输入框 12dp。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),      // 按钮 / 输入框
    medium = RoundedCornerShape(16.dp),     // 卡片
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)