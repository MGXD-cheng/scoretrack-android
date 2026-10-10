package com.lingxi.scoretrack.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lingxi.scoretrack.ui.navigation.AppDestination
import com.lingxi.scoretrack.ui.navigation.navigateToTab

/**
 * 底部导航栏：首页 / 成绩 / 错题 / 图表 / 设置。
 *
 * 选中判定用 destination.hierarchy，保证嵌套图场景下也正确；
 * 跳转统一走 navigateToTab，栈始终是扁平的「startDestination + 当前 Tab」。
 */
@Composable
fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        AppDestination.bottomItems.forEach { destination ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == destination.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = {
                    Text(text = destination.label)
                }
            )
        }
    }
}