package com.lingxi.scoretrack.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lingxi.scoretrack.ui.components.AppBottomBar
import com.lingxi.scoretrack.ui.components.PlaceholderScreen
import com.lingxi.scoretrack.ui.screen.home.HomeScreen

/**
 * 应用导航宿主。底部 5 个 Tab，二级页后续分批接入。
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = { AppBottomBar(navController) }
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
                PlaceholderScreen(
                    title = "成绩",
                    description = "录入与管理每次考试的各科成绩、排名与平均分"
                )
            }
            composable(AppDestination.Mistakes.route) {
                PlaceholderScreen(
                    title = "错题本",
                    description = "记录错题、标注错因、追踪复习状态"
                )
            }
            composable(AppDestination.Charts.route) {
                PlaceholderScreen(
                    title = "图表",
                    description = "成绩趋势折线图与科目雷达图"
                )
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