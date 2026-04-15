package com.lightxin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lightxin.core.auth.SessionManager
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.feature.checkin.ui.CheckinDetailScreen
import com.lightxin.feature.checkin.ui.CheckinListScreen
import com.lightxin.feature.home.ui.HomeScreen
import com.lightxin.feature.labor.ui.LaborDetailScreen
import com.lightxin.feature.labor.ui.LaborSummaryScreen
import com.lightxin.feature.login.ui.LoginScreen

@Composable
fun LightXinNavHost(
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController(),
) {
    // 收集登录状态，决定起始页面
    val isLoggedIn by sessionManager.isLoggedIn.collectAsState(initial = null)

    // 首次加载时等待 DataStore 读取
    val startRoute = when (isLoggedIn) {
        null -> {
            LxLoading()
            return
        }
        true -> Routes.HOME
        false -> Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }

        // Checkin
        composable(Routes.CHECKIN_LIST) { backStackEntry ->
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow("checkin_refresh", false)
                .collectAsState()
            CheckinListScreen(
                onBack = { navController.popBackStack() },
                onTaskClick = { taskDateId ->
                    navController.navigate(Routes.checkinDetail(taskDateId))
                },
                shouldRefresh = shouldRefresh,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["checkin_refresh"] = false
                },
            )
        }
        composable(
            route = Routes.CHECKIN_DETAIL,
            arguments = listOf(navArgument("taskDateId") { type = NavType.StringType }),
        ) {
            CheckinDetailScreen(
                onSubmitSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("checkin_refresh", true)
                },
                onBack = { navController.popBackStack() },
            )
        }

        // Running
        composable(Routes.RUNNING_HOME) {
            // TODO: Phase 6
        }
        composable(Routes.RUNNING_ACTIVE) {
            // TODO: Phase 6
        }
        composable(Routes.RUNNING_SIM) {
            // TODO: Phase 6
        }
        composable(Routes.RUNNING_RESULT) {
            // TODO: Phase 6
        }

        // Labor
        composable(Routes.LABOR_SUMMARY) {
            LaborSummaryScreen(
                onBack = { navController.popBackStack() },
                onActivityClick = { id, type ->
                    navController.navigate(Routes.laborDetail(id, type))
                },
            )
        }
        composable(
            route = Routes.LABOR_DETAIL,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            LaborDetailScreen(
                id = backStackEntry.arguments?.getString("id") ?: "",
                type = backStackEntry.arguments?.getString("type") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
