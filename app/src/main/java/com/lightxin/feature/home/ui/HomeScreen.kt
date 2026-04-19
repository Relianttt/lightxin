package com.lightxin.feature.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.lightxin.core.designsystem.theme.LxInkFaint
import com.lightxin.core.designsystem.theme.LxParchment
import com.lightxin.feature.schedule.ui.ScheduleScreen
import com.lightxin.navigation.Routes

private data class TabItem(
    val label: String,
    val icon: ImageVector,
)

// 全部线性图标：选中态靠陶土色 + 加粗区分，不切换 Filled 版本，
// 避免顶部 Newsreader 衬线与底部 Material filled 的视觉冲突。
private val tabs = listOf(
    TabItem("首页", Icons.Outlined.Home),
    TabItem("课程表", Icons.Outlined.CalendarMonth),
    TabItem("我的", Icons.Outlined.Person),
)

@Composable
fun HomeScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = LxParchment.copy(alpha = 0.92f),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = LxInkFaint,
                            unselectedTextColor = LxInkFaint,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(250)))
                } else {
                    (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(250)))
                }
            },
            label = "tab_content",
        ) { tab ->
            when (tab) {
                0 -> HomeDashboard(
                    viewModel = homeViewModel,
                    modifier = Modifier.padding(padding),
                    navController = navController,
                    onTabSelected = { selectedTab = it },
                )
                1 -> ScheduleScreen(modifier = Modifier.padding(padding))
                2 -> ProfileScreen(
                    modifier = Modifier.padding(padding),
                    onNavigateCheckin = {
                        navController.navigate(Routes.CHECKIN_LIST) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateLabor = {
                        navController.navigate(Routes.LABOR_SUMMARY) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateAiClass = {
                        navController.navigate(Routes.AICLASS_HOME) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateRouteSimulation = {
                        navController.navigate(Routes.RUNNING_ROUTE_SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateAbout = {
                        navController.navigate(Routes.ABOUT) {
                            launchSingleTop = true
                        }
                    },
                    onLogout = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
