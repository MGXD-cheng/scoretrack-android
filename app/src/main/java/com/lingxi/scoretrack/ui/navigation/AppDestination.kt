package com.lingxi.scoretrack.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.lingxi.scoretrack.ui.components.ChartBarIcon

/**
 * 全局路由表。底部导航取 [bottomItems]，二级页只注册路由不占 Tab。
 */
sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {

    data object Home : AppDestination("home", "首页", Icons.Filled.Home)

    data object Scores : AppDestination("scores", "成绩", Icons.AutoMirrored.Filled.List)

    data object Mistakes : AppDestination("mistakes", "错题", Icons.Filled.Warning)

    data object Charts : AppDestination("charts", "图表", ChartBarIcon)

    data object Settings : AppDestination("settings", "设置", Icons.Filled.Settings)

    companion object {

        /** 底部导航展示顺序 */
        val bottomItems: List<AppDestination> = listOf(Home, Scores, Mistakes, Charts, Settings)

        // ---------- 二级页面（不在底部导航） ----------

        const val EXAM_EDIT = "exam_edit"

        /** 编辑考试路由：examId = 0 表示新建 */
        fun examEditRoute(examId: Long = 0L): String = "$EXAM_EDIT?examId=$examId"

        const val SCORE_ENTRY = "score_entry"

        const val PAPER_DETAIL = "paper_detail"

        const val KNOWLEDGE = "knowledge"

        const val AI_CONFIG = "ai_config"

        const val IMPORT = "import"

        const val ANALYSIS = "analysis"
    }
}