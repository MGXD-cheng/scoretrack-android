package com.lingxi.scoretrack.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------- 品牌色（来自设计文档） ----------------
val BrandPrimary = Color(0xFF2A6DF4)      // 主色·蓝
val BrandSecondary = Color(0xFF5B8DEF)    // 辅色·浅蓝

// ---------------- 浅色模式 ----------------
val LightBackground = Color(0xFFF7F9FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF3FB)
val LightTextPrimary = Color(0xFF1A1D26)
val LightTextSecondary = Color(0xFF6B7280)
val LightOutline = Color(0xFFD8DEE9)

// ---------------- 深色模式 ----------------
val DarkBackground = Color(0xFF10131A)
val DarkSurface = Color(0xFF1A1D26)
val DarkSurfaceVariant = Color(0xFF232833)
val DarkTextPrimary = Color(0xFFE8EAF0)
val DarkTextSecondary = Color(0xFF9BA3B4)
val DarkOutline = Color(0xFF3A4150)

// ---------------- 语义色 ----------------
val ColorSuccess = Color(0xFF22C55E)
val ColorWarning = Color(0xFFF59E0B)
val ColorError = Color(0xFFEF4444)

val PrimaryContainerLight = Color(0xFFD9E4FF)
val OnPrimaryContainerLight = Color(0xFF001A44)
val PrimaryContainerDark = Color(0xFF1B3A7A)

/** 图表配色（多科目区分用，尽量高对比且色盲友好） */
val ChartPalette = listOf(
    Color(0xFF2A6DF4),
    Color(0xFF22C55E),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFF8B5CF6),
    Color(0xFF06B6D4),
    Color(0xFFEC4899),
    Color(0xFF84CC16),
    Color(0xFFF97316),
)