package com.lingxi.scoretrack.ui.util

import androidx.compose.ui.graphics.Color

/**
 * 把数据库里存的 `#RRGGBB` / `#AARRGGBB` 字符串转成 Compose 颜色。
 *
 * 为什么不用 `Color(android.graphics.Color.parseColor(hex))` 直接写：
 * 数据是用户可编辑的（科目颜色），一旦存进空串或写错的格式，
 * parseColor 会抛 IllegalArgumentException —— 在 Compose 的测量阶段抛出
 * 异常就是整个页面白屏。这里吞掉异常给一个兜底色，至少界面还在。
 */
fun parseHexColor(hex: String?, fallback: Color = Color(0xFF2A6DF4)): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

/**
 * 科目颜色的可选项。
 *
 * 前 9 个和旧版预置科目的配色一一对应 —— 老用户升级上来，
 * 颜色不会突然变得陌生。后面几个是补充的可选值。
 */
val SubjectColorPalette: List<String> = listOf(
    "#2A6DF4", // 蓝
    "#22C55E", // 绿
    "#F59E0B", // 橙黄
    "#EF4444", // 红
    "#8B5CF6", // 紫
    "#06B6D4", // 青
    "#EC4899", // 粉
    "#84CC16", // 黄绿
    "#F97316", // 橙
    "#64748B", // 灰蓝
    "#14B8A6", // 蓝绿
    "#6366F1"  // 靛蓝
)