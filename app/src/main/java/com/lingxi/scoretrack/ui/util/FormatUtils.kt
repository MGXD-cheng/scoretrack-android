package com.lingxi.scoretrack.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 分数显示：整数不带小数点，其余保留 1 位 */
fun formatScore(value: Float): String =
    if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }

/** 得分率（0.0 ~ 1.0），满分为 0 时返回 0 */
fun scoreRate(score: Float, fullScore: Float): Float =
    if (fullScore <= 0f) 0f else (score / fullScore).coerceIn(0f, 1f)

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

/** 得分率配色：优/良/中/待提升 */
fun scoreColorHex(rate: Float): Long = when {
    rate >= 0.85f -> 0xFF22C55E
    rate >= 0.7f -> 0xFF2A6DF4
    rate >= 0.6f -> 0xFFF59E0B
    else -> 0xFFEF4444
}