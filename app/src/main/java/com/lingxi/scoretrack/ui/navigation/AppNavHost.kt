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
import com.lingxi.scoretrack.ui.screen.charts.ChartsScreen
import com.lingxi.scoretrack.ui.screen.exam.ExamEditScreen
import com.lingxi.scoretrack.ui.screen.home.HomeScreen
import com.lingxi.scoretrack.ui.screen.scores.ScoresScreen

/**
 * 应用导航宿主。底部 5 个 Tab，二级页后续分批接入。
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
                    onOpenScores = { navController.navigate(AppDestination.Scores.route) },
                    onOpenSettings = { navController.navigate(AppDestination.Settings.route) }
                )
            }
            composable(AppDestination.Scores.route) {
                ScoresScreen(
                    onCreateExam = {
                        navController.navigate(AppDestination.examEditRoute())
                    },
                    onEditExam = { examId ->
                        navController.navigate(AppDestination.examEditRoute(examId))
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
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppDestination.Mistakes.route) {
                PlaceholderScreen(
                    title = "错题本",
                    description = "记录错题、标注错因、追踪复习状态"
                )
            }
            composable(AppDestination.Charts.route) {
                ChartsScreen()
            }
            composable(AppDestination.Settings.route) {
                PlaceholderScreen(
                    title = "设置",
                    description = "AI 配置、档案管理与数据导入导出"
                )
            }
        }
    }
}