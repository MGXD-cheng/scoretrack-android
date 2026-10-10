package com.lingxi.scoretrack.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lingxi.scoretrack.ui.components.AppBottomBar
import com.lingxi.scoretrack.ui.components.PlaceholderScreen
import com.lingxi.scoretrack.ui.screen.analysis.AnalysisDetailScreen
import com.lingxi.scoretrack.ui.screen.analysis.AnalysisScreen
import com.lingxi.scoretrack.ui.screen.exam.ExamEditScreen
import com.lingxi.scoretrack.ui.screen.goal.GoalEditScreen
import com.lingxi.scoretrack.ui.screen.home.HomeScreen
import com.lingxi.scoretrack.ui.screen.importsheet.ImportScreen
import com.lingxi.scoretrack.ui.screen.knowledge.KnowledgeScreen
import com.lingxi.scoretrack.ui.screen.mistakes.MistakeDetailScreen
import com.lingxi.scoretrack.ui.screen.mistakes.MistakesScreen
import com.lingxi.scoretrack.ui.screen.paper.PaperEditScreen
import com.lingxi.scoretrack.ui.screen.scores.ScoresScreen
import com.lingxi.scoretrack.ui.screen.settings.AiConfigScreen
import com.lingxi.scoretrack.ui.screen.settings.SettingsScreen

/**
 * 应用导航宿主。底部 4 个 Tab，二级页后续分批接入。
 *
 * 图表曾经也占一个 Tab，但它就是首页该有的主体内容 —— 合并之后
 * 少一个入口，首页也不再是一堆跳转按钮。见 [com.lingxi.scoretrack.ui.components.ChartsSection]。
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = { AppBottomBar(navController) },
        containerColor = MaterialTheme.colorScheme.background,
        // 顶部状态栏交给各页面的 AppTopBar 独占消费；
        // 这里置零，避免「外层 innerPadding + 内层 TopAppBar」把状态栏高度算两遍。
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    // 统一走 Tab 导航，避免出现 [home, settings] 这类额外层级
                    onOpenScores = { navController.navigateToTab(AppDestination.Scores.route) },
                    onOpenSettings = { navController.navigateToTab(AppDestination.Settings.route) },
                    onOpenAnalysis = { navController.navigate(AppDestination.ANALYSIS) },
                    onOpenGoalEdit = { navController.navigate(AppDestination.GOAL_EDIT) }
                )
            }
            composable(AppDestination.Scores.route) {
                ScoresScreen(
                    onCreateExam = {
                        navController.navigate(AppDestination.examEditRoute())
                    },
                    onEditExam = { examId ->
                        navController.navigate(AppDestination.examEditRoute(examId))
                    },
                    onImportSheet = {
                        navController.navigate(AppDestination.IMPORT_SCREEN)
                    }
                )
            }

            composable(
                route = "${AppDestination.EXAM_EDIT}?examId={examId}",
                arguments = listOf(
                    navArgument("examId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                val examId = entry.arguments?.getLong("examId") ?: 0L
                ExamEditScreen(
                    examId = examId,
                    onBack = { navController.popBackStack() },
                    onOpenPaper = { subjectId ->
                        navController.navigate(
                            AppDestination.paperEditRoute(examId, subjectId)
                        )
                    }
                )
            }

            composable(
                route = "${AppDestination.PAPER_EDIT}?examId={examId}&subjectId={subjectId}",
                arguments = listOf(
                    navArgument("examId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("subjectId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                PaperEditScreen(
                    examId = entry.arguments?.getLong("examId") ?: 0L,
                    subjectId = entry.arguments?.getLong("subjectId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppDestination.Mistakes.route) {
                MistakesScreen(
                    onOpenDetail = { mistakeId ->
                        navController.navigate(AppDestination.mistakeDetailRoute(mistakeId))
                    },
                    onOpenKnowledge = {
                        navController.navigate(AppDestination.KNOWLEDGE)
                    }
                )
            }

            composable(AppDestination.KNOWLEDGE) {
                KnowledgeScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = "${AppDestination.MISTAKE_DETAIL}?mistakeId={mistakeId}",
                arguments = listOf(
                    navArgument("mistakeId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                val mistakeId = entry.arguments?.getLong("mistakeId") ?: 0L
                MistakeDetailScreen(
                    mistakeId = mistakeId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppDestination.GOAL_EDIT) {
                GoalEditScreen(onBack = { navController.popBackStack() })
            }

            composable(AppDestination.ANALYSIS) {
                AnalysisScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReport = { reportId ->
                        navController.navigate(AppDestination.analysisDetailRoute(reportId))
                    }
                )
            }

            composable(
                route = "${AppDestination.ANALYSIS_DETAIL}?reportId={reportId}",
                arguments = listOf(
                    navArgument("reportId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                val reportId = entry.arguments?.getLong("reportId") ?: 0L
                AnalysisDetailScreen(
                    reportId = reportId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    onOpenAiConfig = {
                        navController.navigate(AppDestination.AI_CONFIG)
                    },
                    onOpenImport = {
                        navController.navigate(AppDestination.IMPORT_SCREEN)
                    }
                )
            }

            composable(AppDestination.AI_CONFIG) {
                AiConfigScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AppDestination.IMPORT_SCREEN) {
                ImportScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExam = { examId ->
                        // 导入完成后直接跳到考试详情，把「识别 → 核对」串成一条线
                        navController.navigate(AppDestination.examEditRoute(examId))
                    }
                )
            }
        }
    }
}