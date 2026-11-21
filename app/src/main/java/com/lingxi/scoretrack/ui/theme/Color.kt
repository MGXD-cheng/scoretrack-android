package com.lingxi.scoretrack.ui.theme

import androidx.compose.ui.graphics.Color

// ===========================================================================
// 品牌色
// ===========================================================================

val BrandPrimary = Color(0xFF2A6DF4)      // 主色·蓝
val BrandSecondary = Color(0xFF5B8DEF)    // 辅色·浅蓝
val BrandTertiary = Color(0xFF06B6D4)     // 第三色·青（用于点缀与图表）

// ===========================================================================
// 浅色模式
// ===========================================================================

val LightBackground = Color(0xFFF7F9FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF3FB)
val LightSurfaceContainer = Color(0xFFF1F5FC)
val LightSurfaceContainerHigh = Color(0xFFE9EFFA)
val LightTextPrimary = Color(0xFF1A1D26)
val LightTextSecondary = Color(0xFF6B7280)
val LightOutline = Color(0xFFD8DEE9)
val LightOutlineVariant = Color(0xFFE6EAF2)

// ===========================================================================
// 深色模式
// ===========================================================================

val DarkBackground = Color(0xFF10131A)
val DarkSurface = Color(0xFF1A1D26)
val DarkSurfaceVariant = Color(0xFF232833)
val DarkSurfaceContainer = Color(0xFF1E222C)
val DarkSurfaceContainerHigh = Color(0xFF262B36)
val DarkTextPrimary = Color(0xFFE8EAF0)
val DarkTextSecondary = Color(0xFF9BA3B4)
val DarkOutline = Color(0xFF3A4150)
val DarkOutlineVariant = Color(0xFF2C3342)

// ===========================================================================
// 容器色
//
// 这一组是补出来的。之前只定义了 primaryContainer，而代码里已经在用
// secondaryContainer / errorContainer —— 没定义的角色会**静默回落**
// 到 Material3 的默认值（紫色系），于是 FilterChip 的选中态和导入完成页
// 的卡片全是紫的，跟品牌蓝完全不搭。
//
// 教训：ColorScheme 里任何一个角色没定义都不会报错，只会「悄悄变紫」。
// 视觉走查比编译更能抓出这类问题。
// ===========================================================================

val PrimaryContainerLight = Color(0xFFD9E4FF)
val OnPrimaryContainerLight = Color(0xFF001A44)
val PrimaryContainerDark = Color(0xFF1B3A7A)
val OnPrimaryContainerDark = Color(0xFFD9E4FF)

val SecondaryContainerLight = Color(0xFFDCE7FF)
val OnSecondaryContainerLight = Color(0xFF0A2A66)
val SecondaryContainerDark = Color(0xFF22355E)
val OnSecondaryContainerDark = Color(0xFFC7D8FF)

val TertiaryContainerLight = Color(0xFFCCF2FA)
val OnTertiaryContainerLight = Color(0xFF003640)
val TertiaryContainerDark = Color(0xFF14454F)
val OnTertiaryContainerDark = Color(0xFFB6ECF5)

val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)
val ErrorContainerDark = Color(0xFF4A1D1D)
val OnErrorContainerDark = Color(0xFFFECACA)

// ===========================================================================
// 语义色
// ===========================================================================

val ColorSuccess = Color(0xFF22C55E)
val ColorWarning = Color(0xFFF59E0B)
val ColorError = Color(0xFFEF4444)

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
    Color(0xFFF97316)
)