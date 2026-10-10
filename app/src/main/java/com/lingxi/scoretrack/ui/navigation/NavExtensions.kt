package com.lingxi.scoretrack.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * 切换到某个底部 Tab 的统一入口。
 *
 * 关键点：**任何进入 Tab 的地方都必须走这里**（底部导航、首页快捷入口、卡片跳转…）。
 * 之前首页的「设置」图标用的是裸 navigate()，会把目标压在 home 之上，
 * 导致 [home, settings] 这种额外层级和 saveState/restoreState 的配对关系错乱，
 * 表现就是「进了设置之后点主页回不去」。
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}