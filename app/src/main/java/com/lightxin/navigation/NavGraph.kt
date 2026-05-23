package com.lightxin.navigation

import android.net.Uri

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lightxin.core.auth.SessionManager
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.feature.aiclass.ui.AiClassHomeScreen
import com.lightxin.feature.aiclass.ui.AiClassCourseDetailScreen
import com.lightxin.feature.aiclass.ui.AiClassScanScreen
import com.lightxin.feature.aiclass.ui.AiHomeworkDetailScreen
import com.lightxin.feature.about.ui.AboutScreen
import com.lightxin.feature.checkin.ui.CheckinDetailScreen
import com.lightxin.feature.checkin.ui.CheckinListScreen
import com.lightxin.feature.credit.ui.CreditScreen
import com.lightxin.feature.exam.ui.ExamScreen
import com.lightxin.feature.holiday.ui.HolidayRegisterScreen
import com.lightxin.feature.home.ui.HomeScreen
import com.lightxin.feature.labor.ui.LaborSummaryScreen
import com.lightxin.feature.login.ui.LoginScreen
import com.lightxin.feature.more.ui.MoreFeaturesScreen
import com.lightxin.feature.onboarding.ui.OnboardingScreen
import com.lightxin.feature.running.ui.RouteSimulationSettingsScreen
import com.lightxin.feature.running.ui.RouteTemplateDetailScreen
import com.lightxin.feature.running.ui.RouteTemplateListScreen
import com.lightxin.feature.running.ui.RouteTemplateRecordScreen
import com.lightxin.feature.running.ui.RouteTemplateViewModel
import com.lightxin.feature.running.ui.RunningActiveScreen
import com.lightxin.feature.running.ui.RunningHomeScreen
import com.lightxin.feature.running.ui.RunningResultScreen
import com.lightxin.feature.running.ui.RunningSimScreen
import com.lightxin.feature.running.ui.RunningViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable
fun LightXinNavHost(
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController(),
    shortcutTarget: ShortcutTarget? = null,
    pendingDormTaskId: String? = null,
    isDormShortcutResolved: Boolean = false,
    pendingNotificationRoute: String? = null,
    onShortcutConsumed: () -> Unit = {},
) {
    // 合并 onboarded + loggedIn 两态决定起始页
    val startStateFlow = remember(sessionManager) {
        combine(sessionManager.isOnboarded, sessionManager.isLoggedIn) { onboarded, loggedIn ->
            when {
                !onboarded -> Routes.ONBOARDING
                !loggedIn -> Routes.LOGIN
                else -> Routes.HOME
            }
        }
    }
    val startRoute by startStateFlow.collectAsState(initial = null)

    val resolvedStartRoute = startRoute
    if (resolvedStartRoute == null) {
        LxLoading()
        return
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentOnShortcutConsumed by rememberUpdatedState(onShortcutConsumed)

    LaunchedEffect(shortcutTarget, resolvedStartRoute, pendingDormTaskId, isDormShortcutResolved) {
        val target = shortcutTarget ?: return@LaunchedEffect
        if (resolvedStartRoute != Routes.HOME) return@LaunchedEffect
        if (target == ShortcutTarget.DORM_CHECKIN && !isDormShortcutResolved) return@LaunchedEffect

        navController.navigate(Routes.HOME) {
            launchSingleTop = true
            popUpTo(Routes.HOME) { inclusive = false }
        }
        when (target) {
            ShortcutTarget.SCAN_CHECKIN -> {
                navController.navigate(Routes.AICLASS_HOME) { launchSingleTop = true }
                navController.navigate(Routes.AICLASS_SCAN) { launchSingleTop = true }
            }

            ShortcutTarget.DORM_CHECKIN -> {
                navController.navigate(Routes.CHECKIN_LIST) { launchSingleTop = true }
                if (pendingDormTaskId != null) {
                    navController.navigate(Routes.checkinDetail(pendingDormTaskId)) { launchSingleTop = true }
                } else {
                    Toast.makeText(context, "当前没有待签到的查寝任务", Toast.LENGTH_SHORT).show()
                }
            }
        }
        currentOnShortcutConsumed()
    }

    // 通知点击路由
    LaunchedEffect(pendingNotificationRoute) {
        val route = pendingNotificationRoute ?: return@LaunchedEffect
        if (resolvedStartRoute != Routes.HOME) return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        currentOnShortcutConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStartRoute,
        enterTransition = {
            fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 }
        },
        exitTransition = {
            fadeOut(tween(200))
        },
        popEnterTransition = {
            fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 4 }
        },
        popExitTransition = {
            fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 4 }
        },
    ) {
        composable(
            Routes.ONBOARDING,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(300)) },
        ) {
            val context = LocalContext.current
            OnboardingScreen(
                onAcknowledge = {
                    scope.launch {
                        sessionManager.markOnboarded()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                },
                onDismiss = {
                    context.findActivity()?.let { activity ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            activity.finishAndRemoveTask()
                        } else {
                            activity.finishAffinity()
                        }
                    }
                },
            )
        }

        composable(
            Routes.LOGIN,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(300)) },
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Routes.HOME,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(200)) },
        ) {
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
                onHolidayClick = { holidayId ->
                    navController.navigate(Routes.holidayRegister(holidayId))
                },
                shouldRefresh = shouldRefresh,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["checkin_refresh"] = false
                },
            )
        }
        composable(
            route = Routes.HOLIDAY_REGISTER,
            arguments = listOf(navArgument("holidayId") { type = NavType.StringType }),
        ) { backStackEntry ->
            HolidayRegisterScreen(
                holidayId = backStackEntry.arguments?.getString("holidayId") ?: "",
                onSubmitSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("checkin_refresh", true)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
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
        composable(Routes.RUNNING_HOME) { backStackEntry ->
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.HOME) }
            val runningViewModel: RunningViewModel = hiltViewModel(homeEntry)
            RunningHomeScreen(
                viewModel = runningViewModel,
                onBack = { navController.popBackStack() },
                onOpenActive = {
                    navController.navigate(Routes.RUNNING_ACTIVE) {
                        launchSingleTop = true
                    }
                },
                onOpenSim = {
                    navController.navigate(Routes.RUNNING_SIM) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.RUNNING_ACTIVE) { backStackEntry ->
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.HOME) }
            val runningViewModel: RunningViewModel = hiltViewModel(homeEntry)
            RunningActiveScreen(
                viewModel = runningViewModel,
                onBack = { navController.popBackStack() },
                onNavigateResult = {
                    navController.navigate(Routes.RUNNING_RESULT) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.RUNNING_SIM) { backStackEntry ->
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.HOME) }
            val runningViewModel: RunningViewModel = hiltViewModel(homeEntry)
            RunningSimScreen(
                viewModel = runningViewModel,
                onBack = { navController.popBackStack() },
                onNavigateResult = {
                    navController.navigate(Routes.RUNNING_RESULT) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.RUNNING_RESULT) { backStackEntry ->
            val homeEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.HOME) }
            val runningViewModel: RunningViewModel = hiltViewModel(homeEntry)
            RunningResultScreen(
                viewModel = runningViewModel,
                onBack = {
                    navController.navigate(Routes.RUNNING_HOME) {
                        popUpTo(Routes.RUNNING_HOME) { inclusive = true }
                    }
                },
                onBackToRunning = {
                    navController.navigate(Routes.RUNNING_HOME) {
                        popUpTo(Routes.RUNNING_HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBackToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }

        // Route Simulation (Phase 1)
        composable(Routes.RUNNING_ROUTE_SETTINGS) { backStackEntry ->
            val settingsEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.RUNNING_ROUTE_SETTINGS)
            }
            val routeVm: RouteTemplateViewModel = hiltViewModel(settingsEntry)
            RouteSimulationSettingsScreen(
                viewModel = routeVm,
                onBack = { navController.popBackStack() },
                onOpenRecord = {
                    navController.navigate(Routes.RUNNING_ROUTE_RECORD) { launchSingleTop = true }
                },
                onOpenList = {
                    navController.navigate(Routes.RUNNING_ROUTE_LIST) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.RUNNING_ROUTE_RECORD) { backStackEntry ->
            val settingsEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.RUNNING_ROUTE_SETTINGS)
            }
            val routeVm: RouteTemplateViewModel = hiltViewModel(settingsEntry)
            RouteTemplateRecordScreen(
                viewModel = routeVm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RUNNING_ROUTE_LIST) { backStackEntry ->
            val settingsEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.RUNNING_ROUTE_SETTINGS)
            }
            val routeVm: RouteTemplateViewModel = hiltViewModel(settingsEntry)
            RouteTemplateListScreen(
                viewModel = routeVm,
                onBack = { navController.popBackStack() },
                onOpenDetail = { id ->
                    navController.navigate(Routes.runningRouteDetail(id)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.RUNNING_ROUTE_DETAIL,
            arguments = listOf(navArgument("templateId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val settingsEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.RUNNING_ROUTE_SETTINGS)
            }
            val routeVm: RouteTemplateViewModel = hiltViewModel(settingsEntry)
            RouteTemplateDetailScreen(
                viewModel = routeVm,
                templateId = backStackEntry.arguments?.getString("templateId") ?: "",
                onBack = { navController.popBackStack() },
            )
        }

        // Labor
        composable(Routes.LABOR_SUMMARY) {
            LaborSummaryScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // AI Class
        composable(Routes.AICLASS_HOME) { backStackEntry ->
            val aiClassEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.AICLASS_HOME) }
            val aiClassViewModel: com.lightxin.feature.aiclass.ui.AiClassViewModel = hiltViewModel(aiClassEntry)
            AiClassHomeScreen(
                viewModel = aiClassViewModel,
                onBack = { navController.popBackStack() },
                onOpenScan = {
                    navController.navigate(Routes.AICLASS_SCAN) {
                        launchSingleTop = true
                    }
                },
                onOpenCourseDetail = { classId ->
                    navController.navigate(Routes.aiClassDetail(classId)) {
                        launchSingleTop = true
                    }
                },
                onOpenWorkingDetail = {
                    aiClassViewModel.openWorkingRecordDetail()
                    navController.navigate(Routes.aiClassDetail("_working")) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.AICLASS_SCAN) { backStackEntry ->
            val aiClassEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.AICLASS_HOME) }
            val aiClassViewModel: com.lightxin.feature.aiclass.ui.AiClassViewModel = hiltViewModel(aiClassEntry)
            AiClassScanScreen(
                onBack = { navController.popBackStack() },
                onScanResult = { payload ->
                    aiClassViewModel.submitQrCode(payload)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = Routes.AICLASS_DETAIL,
            arguments = listOf(navArgument("classId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val aiClassEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.AICLASS_HOME) }
            val aiClassViewModel: com.lightxin.feature.aiclass.ui.AiClassViewModel = hiltViewModel(aiClassEntry)
            AiClassCourseDetailScreen(
                classId = Uri.decode(backStackEntry.arguments?.getString("classId").orEmpty()),
                viewModel = aiClassViewModel,
                onBack = { navController.popBackStack() },
                onOpenHomeworkDetail = { cwId ->
                    val selectedCourse = aiClassViewModel.uiState.value.selectedCourse
                    val teachClassId = selectedCourse?.teachClassId.orEmpty()
                        .ifBlank { selectedCourse?.classId.orEmpty() }
                    navController.navigate(Routes.aiClassHomeworkDetail(cwId, teachClassId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        // AI Homework Detail
        composable(
            route = Routes.AICLASS_HOMEWORK_DETAIL,
            arguments = listOf(
                navArgument("cwId") { type = NavType.StringType },
                navArgument("teachClassId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            AiHomeworkDetailScreen(
                cwId = Uri.decode(backStackEntry.arguments?.getString("cwId").orEmpty()),
                teachClassId = Uri.decode(backStackEntry.arguments?.getString("teachClassId").orEmpty()),
                onBack = { navController.popBackStack() },
            )
        }

        // More Features
        composable(Routes.MORE_FEATURES) {
            MoreFeaturesScreen(
                onBack = { navController.popBackStack() },
                onNavigateLabor = {
                    navController.navigate(Routes.LABOR_SUMMARY) { launchSingleTop = true }
                },
                onNavigateExam = {
                    navController.navigate(Routes.EXAM_SCORES) { launchSingleTop = true }
                },
                onNavigateCredit = {
                    navController.navigate(Routes.CREDIT_OVERVIEW) { launchSingleTop = true }
                },
            )
        }

        // Exam
        composable(Routes.EXAM_SCORES) {
            ExamScreen(onBack = { navController.popBackStack() })
        }

        // Credit
        composable(Routes.CREDIT_OVERVIEW) {
            CreditScreen(onBack = { navController.popBackStack() })
        }

        // About
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
