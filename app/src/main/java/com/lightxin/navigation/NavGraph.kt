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
import com.lightxin.feature.home.ui.HomeScreen
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
        composable(Routes.CHECKIN_LIST) {
            // TODO: Phase 5
        }
        composable(
            route = Routes.CHECKIN_DETAIL,
            arguments = listOf(navArgument("taskDateId") { type = NavType.StringType }),
        ) {
            // TODO: Phase 5
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
            // TODO: Phase 4
        }
        composable(
            route = Routes.LABOR_DETAIL,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
            ),
        ) {
            // TODO: Phase 4
        }
    }
}
