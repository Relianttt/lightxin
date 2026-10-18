package com.lightxin.navigation

import android.net.Uri

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.lightxin.feature.leave.ui.LeaveCreateScreen
import com.lightxin.feature.leave.ui.LeaveDetailScreen
import com.lightxin.feature.leave.ui.LeaveListScreen
import com.lightxin.feature.repair.ui.RepairCreateScreen
import com.lightxin.feature.repair.ui.RepairDetailScreen
import com.lightxin.feature.repair.ui.RepairListScreen
import com.lightxin.feature.login.ui.LoginScreen
import com.lightxin.feature.more.ui.MoreFeaturesScreen
import com.lightxin.feature.onboarding.ui.OnboardingScreen
import com.lightxin.feature.running.ui.RouteSimulationSettingsScreen
import com.lightxin.feature.running.ui.RouteTemplateImportScreen
import com.lightxin.feature.running.ui.RouteTemplateDetailScreen
import com.lightxin.feature.running.ui.RouteTemplateListScreen
import com.lightxin.feature.running.ui.RouteTemplateRecordScreen
import com.lightxin.feature.running.ui.RouteTemplateViewModel
import com.lightxin.feature.running.ui.RunningActiveScreen
import com.lightxin.feature.running.ui.RunningHomeScreen
import com.lightxin.feature.running.exercise.ui.ClubDetailScreen
import com.lightxin.feature.running.exercise.ui.ExerciseCheckScreen
import com.lightxin.feature.running.ui.RunningResultScreen
import com.lightxin.feature.running.ui.RunningSimScreen
import com.lightxin.feature.running.ui.RunningViewModel
import com.lightxin.core.designsystem.theme.LxMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
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
    val fifReloginViewModel: FifReloginViewModel = hiltViewModel()
    var isReloggingIn by remember { mutableStateOf(false) }
    val onFifRelogin: () -> Unit = {
        if (!isReloggingIn) {
            isReloggingIn = true
            scope.launch {
                try {
                    fifReloginViewModel.prepareRelogin()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, "暂时无法打开重新登录，请重试", Toast.LENGTH_SHORT).show()
                } finally {
                    isReloggingIn = false
                }
            }
        }
    }
    // 只失效校内登录态时用：不清 FIF 会话
    val onSessionRelogin: () -> Unit = {
        if (!isReloggingIn) {
            isReloggingIn = true
            scope.launch {
                try {
                    fifReloginViewModel.prepareSessionRelogin()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, "暂时无法打开重新登录，请重试", Toast.LENGTH_SHORT).show()
                } finally {
                    isReloggingIn = false
                }
            }
        }
    }
    val currentOnShortcutConsumed by rememberUpdatedState(onShortcutConsumed)

    // shortcut 落位静态化（仿 LibChecker setCurrentItem(index, false)）：
    // 链式 navigate 期间禁用 NavHost 转场，避免 splash 后再叠加多段滑动动画
    var suppressTransitions by remember { mutableStateOf(false) }
    LaunchedEffect(suppressTransitions) {
        if (suppressTransitions) {
            delay(600)
            suppressTransitions = false
        }
    }

    LaunchedEffect(shortcutTarget, resolvedStartRoute, pendingDormTaskId, isDormShortcutResolved) {
        val target = shortcutTarget ?: return@LaunchedEffect
        if (resolvedStartRoute != Routes.HOME) return@LaunchedEffect
        if (target == ShortcutTarget.DORM_CHECKIN && !isDormShortcutResolved) return@LaunchedEffect

        suppressTransitions = true
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

    SharedTransitionLayout {
    NavHost(
        navController = navController,
        startDestination = resolvedStartRoute,
        enterTransition = {
            if (suppressTransitions) EnterTransition.None
            else fadeIn(tween(LxMotion.PageMs, easing = LxMotion.EaseOut)) +
                slideInHorizontally(tween(LxMotion.PageMs, easing = LxMotion.EaseOut)) {
                    (it * LxMotion.PageShiftFraction).toInt()
                }
        },
        exitTransition = {
            if (suppressTransitions) ExitTransition.None
            else fadeOut(tween(LxMotion.StateMs, easing = LxMotion.EaseOut))
        },
        popEnterTransition = {
            if (suppressTransitions) EnterTransition.None
            else fadeIn(tween(LxMotion.StateMs + 40, easing = LxMotion.EaseOut)) +
                slideInHorizontally(tween(LxMotion.PageMs, easing = LxMotion.EaseOut)) {
                    (-it * LxMotion.PagePopShiftFraction).toInt()
                }
        },
        popExitTransition = {
            if (suppressTransitions) ExitTransition.None
            else fadeOut(tween(LxMotion.PagePopExitMs, easing = LxMotion.EaseOut)) +
                slideOutHorizontally(tween(LxMotion.PagePopExitMs, easing = LxMotion.EaseOut)) {
                    (it * LxMotion.PagePopShiftFraction).toInt()
                }
        },
    ) {
        composable(
            Routes.ONBOARDING,
            enterTransition = { fadeIn(tween(LxMotion.PageMs, easing = LxMotion.EaseOut)) },
            exitTransition = { fadeOut(tween(LxMotion.StateMs, easing = LxMotion.EaseOut)) },
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
            enterTransition = { fadeIn(tween(LxMotion.PageMs, easing = LxMotion.EaseOut)) },
            exitTransition = { fadeOut(tween(LxMotion.StateMs, easing = LxMotion.EaseOut)) },
        ) {
            LoginScreen(
                onLoginSuccess = {
                    scope.launch { fifReloginViewModel.onLoginSuccess() }
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Routes.HOME,
            enterTransition = { fadeIn(tween(LxMotion.PageMs, easing = LxMotion.EaseOut)) },
            exitTransition = { fadeOut(tween(LxMotion.StateMs, easing = LxMotion.EaseOut)) },
        ) {
            HomeScreen(navController = navController, onRelogin = onFifRelogin)
        }

        // Checkin
        composable(Routes.CHECKIN_LIST) { backStackEntry ->
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow("checkin_refresh", false)
                .collectAsState()
            CheckinListScreen(
                onBack = { navController.popBackStack() },
                onRelogin = onSessionRelogin,
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
                onOpenClub = {
                    navController.navigate(Routes.RUNNING_CLUB_DETAIL) {
                        launchSingleTop = true
                    }
                },
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
        composable(Routes.RUNNING_CLUB_DETAIL) {
            ClubDetailScreen(
                onBack = { navController.popBackStack() },
                onStartCheck = { autoId, memberId ->
                    navController.navigate(Routes.runningExerciseCheck(autoId, memberId)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = Routes.RUNNING_EXERCISE_CHECK,
            arguments = listOf(
                navArgument("autoId") { type = NavType.StringType },
                navArgument("memberId") { type = NavType.StringType },
            ),
        ) {
            ExerciseCheckScreen(
                onBack = { navController.popBackStack() },
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
                onOpenImport = {
                    navController.navigate(Routes.RUNNING_ROUTE_IMPORT) { launchSingleTop = true }
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
                onImport = {
                    navController.navigate(Routes.RUNNING_ROUTE_IMPORT) { launchSingleTop = true }
                },
                onOpenDetail = { id ->
                    navController.navigate(Routes.runningRouteDetail(id)) { launchSingleTop = true }
                },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
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
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
            )
        }
        composable(Routes.RUNNING_ROUTE_IMPORT) { backStackEntry ->
            val settingsEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.RUNNING_ROUTE_SETTINGS)
            }
            val routeVm: RouteTemplateViewModel = hiltViewModel(settingsEntry)
            RouteTemplateImportScreen(
                viewModel = routeVm,
                onBack = { navController.popBackStack() },
                onImported = { navController.popBackStack() },
            )
        }

        // Labor
        composable(Routes.LABOR_SUMMARY) {
            LaborSummaryScreen(
                onBack = { navController.popBackStack() },
                onRelogin = onSessionRelogin,
            )
        }

        // AI Class
        composable(Routes.AICLASS_HOME) { backStackEntry ->
            val aiClassEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.AICLASS_HOME) }
            val aiClassViewModel: com.lightxin.feature.aiclass.ui.AiClassViewModel = hiltViewModel(aiClassEntry)
            AiClassHomeScreen(
                viewModel = aiClassViewModel,
                onRelogin = onFifRelogin,
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
                onRelogin = onFifRelogin,
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
                onRelogin = onFifRelogin,
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
                onNavigateLeave = {
                    navController.navigate(Routes.LEAVE_LIST) { launchSingleTop = true }
                },
                onNavigateRepair = {
                    navController.navigate(Routes.REPAIR_LIST) { launchSingleTop = true }
                },
            )
        }

        // Leave
        composable(Routes.LEAVE_LIST) { backStackEntry ->
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow("leave_refresh", false)
                .collectAsState()
            LeaveListScreen(
                onBack = { navController.popBackStack() },
                onCreate = {
                    navController.navigate(Routes.LEAVE_CREATE) { launchSingleTop = true }
                },
                onOpenDetail = { id ->
                    navController.navigate(Routes.leaveDetail(id)) { launchSingleTop = true }
                },
                shouldRefresh = shouldRefresh,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["leave_refresh"] = false
                },
            )
        }
        composable(Routes.LEAVE_CREATE) {
            LeaveCreateScreen(
                onBack = { navController.popBackStack() },
                onSubmitted = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("leave_refresh", true)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = Routes.LEAVE_DETAIL,
            arguments = listOf(navArgument("executionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            LeaveDetailScreen(
                executionId = Uri.decode(backStackEntry.arguments?.getString("executionId").orEmpty()),
                onBack = { navController.popBackStack() },
                onChanged = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("leave_refresh", true)
                },
            )
        }

        // Repair
        composable(Routes.REPAIR_LIST) { backStackEntry ->
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow("repair_refresh", false)
                .collectAsState()
            RepairListScreen(
                onBack = { navController.popBackStack() },
                onCreate = {
                    navController.navigate(Routes.REPAIR_CREATE) { launchSingleTop = true }
                },
                onOpenDetail = { bxdh ->
                    navController.navigate(Routes.repairDetail(bxdh)) { launchSingleTop = true }
                },
                shouldRefresh = shouldRefresh,
                onRefreshConsumed = {
                    backStackEntry.savedStateHandle["repair_refresh"] = false
                },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
            )
        }
        composable(Routes.REPAIR_CREATE) {
            RepairCreateScreen(
                onBack = { navController.popBackStack() },
                onSubmitted = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("repair_refresh", true)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = Routes.REPAIR_DETAIL,
            arguments = listOf(navArgument("bxdh") { type = NavType.StringType }),
        ) { backStackEntry ->
            RepairDetailScreen(
                bxdh = Uri.decode(backStackEntry.arguments?.getString("bxdh").orEmpty()),
                onBack = { navController.popBackStack() },
                onChanged = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("repair_refresh", true)
                },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
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
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
