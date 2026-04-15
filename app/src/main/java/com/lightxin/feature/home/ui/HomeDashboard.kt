package com.lightxin.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxShimmerCard
import com.lightxin.core.designsystem.theme.LxCategoryColors
import com.lightxin.core.designsystem.theme.LxSuccess
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.labor.domain.HoursSummary
import com.lightxin.feature.running.domain.RunningDashboard
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.navigation.Routes
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController,
    onTabSelected: (Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // 问候区
            GreetingSection(
                userName = uiState.userName,
                dashboardData = uiState.dashboardData,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                repeat(4) {
                    LxShimmerCard()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                val data = uiState.dashboardData
                val errors = uiState.errors

                // 今日课程
                StaggeredCard(index = 0) {
                    TodayCourseCard(
                        courses = data.todayCourses,
                        currentWeek = data.currentWeek,
                        error = errors["schedule"],
                        onClick = { onTabSelected(1) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 查寝签到
                StaggeredCard(index = 1) {
                    CheckinCard(
                        task = data.nextCheckin,
                        error = errors["checkin"],
                        onClick = {
                            navController.navigate(Routes.CHECKIN_LIST) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 运动进度
                StaggeredCard(index = 2) {
                    RunningCard(
                        dashboard = data.runningProgress,
                        error = errors["running"],
                        onClick = {
                            navController.navigate(Routes.RUNNING_HOME) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 劳动工时
                StaggeredCard(index = 3) {
                    LaborCard(
                        hours = data.laborHours,
                        error = errors["labor"],
                        onClick = {
                            navController.navigate(Routes.LABOR_SUMMARY) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingSection(userName: String, dashboardData: HomeDashboardData) {
    val hour = remember { LocalTime.now().hour }
    val greeting = remember(hour) {
        when (hour) {
            in 0..5 -> "夜深了"
            in 6..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
    }

    Text(
        text = if (userName.isNotBlank()) "$greeting，$userName" else greeting,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(4.dp))

    val subtitle = remember(dashboardData, hour) {
        buildSmartSubtitle(dashboardData, hour)
    }

    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun buildSmartSubtitle(data: HomeDashboardData, hour: Int): String {
    val isLateNight = hour in 0..5

    // 凌晨优先 — 不依赖异步数据，避免加载前后文案跳变
    if (isLateNight) {
        return "这么晚了还没睡？注意身体哦"
    }

    // 23点 + 明天有早课
    if (hour >= 23 && data.tomorrowFirstSection != null && data.tomorrowFirstSection <= 2) {
        return "明天有早课，记得早点休息哦"
    }

    // 晚上 + 有未签到任务
    if (hour >= 19 && data.nextCheckin != null) {
        return "别忘了完成查寝签到哦"
    }

    // 深夜但不是凌晨
    if (hour >= 23) {
        return "夜深了，早点休息"
    }

    // 今天没课
    if (data.todayCourses.isEmpty() && data.currentWeek > 0) {
        return when (hour) {
            in 6..9 -> "今天没课，可以多睡一会儿"
            in 10..11 -> "今天没课，享受悠闲的上午"
            in 12..13 -> "今天没课，午饭后休息一下"
            in 14..17 -> "今天没课，下午自由安排"
            in 18..20 -> "今天没课的一天，轻松惬意"
            else -> "今天没课，好好放松"
        }
    }

    // 有课但都上完了
    if (data.todayCourses.isNotEmpty()) {
        val lastSection = data.todayCourses.maxOf { it.endSection }
        val approximateEndHour = 8 + lastSection
        if (hour >= approximateEndHour) {
            return "今天的课结束了，辛苦啦"
        }
    }

    // 默认 — 按时段
    return when (hour) {
        in 6..8 -> "新的一天，元气满满"
        in 9..11 -> "上午加油，效率拉满"
        in 12..13 -> "午间时光，记得吃饭"
        in 14..17 -> "下午继续，保持状态"
        in 18..20 -> "傍晚了，今天辛苦了"
        in 21..22 -> "晚间时光，放松一下"
        else -> "轻小信，陪你度过每一天"
    }
}

@Composable
private fun StaggeredCard(
    index: Int,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 },
    ) {
        content()
    }
}

// ─── 今日课程卡片 ───

@Composable
private fun TodayCourseCard(
    courses: List<Course>,
    currentWeek: Int,
    error: String?,
    onClick: () -> Unit,
) {
    DashboardCard(
        icon = Icons.Default.CalendarMonth,
        title = "今日课程",
        badge = if (currentWeek > 0) "第${currentWeek}周" else null,
        onClick = onClick,
    ) {
        if (error != null) {
            ErrorHint(error)
        } else if (courses.isEmpty()) {
            Text(
                text = "今天没有课，好好休息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            courses.take(3).forEach { course ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = LxCategoryColors[
                        (course.name.hashCode() and 0x7FFFFFFF) % LxCategoryColors.size
                    ]
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "第${course.startSection}-${course.endSection}节 · ${course.room}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (courses.size > 3) {
                Text(
                    text = "还有 ${courses.size - 3} 节课...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ─── 查寝签到卡片 ───

@Composable
private fun CheckinCard(
    task: CheckinTask?,
    error: String?,
    onClick: () -> Unit,
) {
    DashboardCard(
        icon = Icons.Default.Bed,
        title = "查寝签到",
        onClick = onClick,
    ) {
        if (error != null) {
            ErrorHint(error)
        } else if (task == null) {
            Text(
                text = "暂无待签到任务",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = task.taskName.ifBlank { "查寝签到" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(task.startTime)
                    if (task.endTime.isNotBlank()) append(" ~ ${task.endTime}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── 运动进度卡片 ───

@Composable
private fun RunningCard(
    dashboard: RunningDashboard?,
    error: String?,
    onClick: () -> Unit,
) {
    DashboardCard(
        icon = Icons.Default.DirectionsRun,
        title = "运动进度",
        onClick = onClick,
    ) {
        if (error != null) {
            ErrorHint(error)
        } else if (dashboard == null) {
            Text(
                text = "暂无运动数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 环形进度
                val progress = if (dashboard.taskTargetKm > 0) {
                    (dashboard.completedKm / dashboard.taskTargetKm).toFloat().coerceIn(0f, 1f)
                } else 0f

                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = String.format(Locale.CHINA, "%.0f%%", progress * 100),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column {
                    Text(
                        text = "今日 ${String.format(Locale.CHINA, "%.2f", dashboard.todayKm)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (dashboard.taskTargetKm > 0) {
                            "已完成 ${formatKmCompact(dashboard.completedKm)} / ${formatKmCompact(dashboard.taskTargetKm)} km"
                        } else {
                            "累计 ${formatKmCompact(dashboard.completedKm)} km"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── 劳动工时卡片 ───

@Composable
private fun LaborCard(
    hours: HoursSummary?,
    error: String?,
    onClick: () -> Unit,
) {
    DashboardCard(
        icon = Icons.Default.WorkHistory,
        title = "劳动教育",
        badge = hours?.let { "共 ${"%.1f".format(it.totalTimes)} 时长" },
        onClick = onClick,
    ) {
        if (error != null) {
            ErrorHint(error)
        } else if (hours == null) {
            Text(
                text = "暂无工时数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val items = listOf(
                "志愿" to hours.voluntaryTimes,
                "暑期" to hours.summerTimes,
                "劳动" to hours.laborTimes,
                "社区" to hours.socialTimes,
                "其他" to hours.otherTimes,
            )
            val maxHours = items.maxOf { it.second }.coerceAtLeast(1.0)

            items.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        val fraction = (value / maxHours).toFloat().coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(LxCategoryColors[index]),
                            )
                        }
                    }
                    Text(
                        text = "%.1f".format(value),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ─── 通用卡片容器 ───

@Composable
private fun DashboardCard(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ErrorHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun formatKmCompact(value: Double): String =
    if (value % 1.0 == 0.0) {
        String.format(Locale.CHINA, "%.0f", value)
    } else {
        String.format(Locale.CHINA, "%.1f", value)
    }
