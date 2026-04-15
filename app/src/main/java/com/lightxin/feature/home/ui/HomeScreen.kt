package com.lightxin.feature.home.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.lightxin.feature.running.ui.RunningHomeScreen
import com.lightxin.feature.running.ui.RunningViewModel
import com.lightxin.feature.schedule.ui.ScheduleScreen

private data class TabItem(
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem("首页", Icons.Default.Home),
    TabItem("课程表", Icons.Default.CalendarMonth),
    TabItem("跑步", Icons.Default.DirectionsRun),
    TabItem("我的", Icons.Default.Person),
)

@Composable
fun HomeScreen(
    navController: NavHostController,
    runningViewModel: RunningViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> HomeDashboard(
                modifier = Modifier.padding(padding),
                navController = navController,
                onTabSelected = { selectedTab = it },
            )
            1 -> ScheduleScreen(modifier = Modifier.padding(padding))
            2 -> RunningHomeScreen(
                modifier = Modifier.padding(padding),
                viewModel = runningViewModel,
                onOpenActive = {
                    navController.navigate(com.lightxin.navigation.Routes.RUNNING_ACTIVE) {
                        launchSingleTop = true
                    }
                },
                onOpenSim = {
                    navController.navigate(com.lightxin.navigation.Routes.RUNNING_SIM) {
                        launchSingleTop = true
                    }
                },
            )
            3 -> ProfilePlaceholder(modifier = Modifier.padding(padding))
        }
    }
}
