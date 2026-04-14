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
import com.lightxin.feature.home.ui.HomeScreen
import com.lightxin.feature.login.ui.LoginScreen
import javax.inject.Inject

@Composable
fun LightXinNavHost(
    navController: NavHostController = rememberNavController(),
) {
    // TODO: 根据登录状态决定起始页面（Phase 2完成后改为动态判断）
    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
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
