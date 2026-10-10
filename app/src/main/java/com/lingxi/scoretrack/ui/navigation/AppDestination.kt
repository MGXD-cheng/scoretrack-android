package com.lingxi.scoretrack.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

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

    data object Settings : AppDestination("settings", "设置", Icons.Filled.Settings)

    companion object {

        /**
         * 底部导航展示顺序。
         *
         * 「图表」不再单独占位 —— 它的内容已经并进首页（见 ChartsSection）。
         */
        val bottomItems: List<AppDestination> = listOf(Home, Scores, Mistakes, Settings)

        // ---------- 二级页面（不在底部导航） ----------

        const val EXAM_EDIT = "exam_edit"

        /** 编辑考试路由：examId = 0 表示新建 */
        fun examEditRoute(examId: Long = 0L): String = "$EXAM_EDIT?examId=$examId"

        const val MISTAKE_DETAIL = "mistake_detail"

        /** 拍照识别成绩单 */
        const val IMPORT_SCREEN = "import_screen"

        /** AI 诊断报告详情（列表页复用下方已有的 ANALYSIS 常量） */
        const val ANALYSIS_DETAIL = "analysis_detail"

        /** 录小题分（试卷编辑） */
        const val PAPER_EDIT = "paper_edit"

        /** 考试目标设定 */
        const val GOAL_EDIT = "goal_edit"

        /** 试卷编辑路由 */
        fun paperEditRoute(examId: Long, subjectId: Long): String =
            "$PAPER_EDIT?examId=$examId&subjectId=$subjectId"

        /** 诊断报告详情路由 */
        fun analysisDetailRoute(reportId: Long): String = "$ANALYSIS_DETAIL?reportId=$reportId"

        /** 错题详情路由 */
        fun mistakeDetailRoute(mistakeId: Long): String = "$MISTAKE_DETAIL?mistakeId=$mistakeId"

        const val SCORE_ENTRY = "score_entry"

        const val PAPER_DETAIL = "paper_detail"

        const val KNOWLEDGE = "knowledge"

        const val AI_CONFIG = "ai_config"

        const val IMPORT = "import"

        const val ANALYSIS = "analysis"
    }
}