package com.lingxi.scoretrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 浅色方案。
 *
 * **每个用得到的角色都要显式给值**。没给的角色不会报错，只会静默回落到
 * Material3 的默认色板（紫色系）—— 曾经 secondaryContainer 就是这么
 * 漏掉的，结果 FilterChip 选中态和导入完成页的卡片全是紫的。
 *
 * 改这个文件时建议顺手 grep 一下 colorScheme. 的用法，确认没有角色漏网。
 */
private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,

    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,

    tertiary = BrandTertiary,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,

    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,

    error = ColorError,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,

    scrim = Color.Black,
    inverseSurface = LightTextPrimary,
    inverseOnSurface = LightSurface
)

private val DarkColors = darkColorScheme(
    primary = BrandSecondary,
    onPrimary = Color(0xFF0A1B3D),
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,

    secondary = BrandPrimary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,

    tertiary = BrandTertiary,
    onTertiary = Color(0xFF00303A),
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,

    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,

    error = ColorError,
    onError = Color.White,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,

    scrim = Color.Black,
    inverseSurface = DarkTextPrimary,
    inverseOnSurface = DarkSurface
)

/**
 * 学迹主题。
 *
 * 刻意不启用动态取色（Dynamic Color）：品牌蓝是这个应用视觉识别的一部分，
 * 跟着系统壁纸变成任意颜色反而会失去一致性。用户换壁纸，图标和界面
 * 应该还是同一个「学迹」。
 */
@Composable
fun ScoreTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}