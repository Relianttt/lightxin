package com.lightxin.feature.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxShimmerCard
import com.lightxin.core.designsystem.theme.LxAmber
import com.lightxin.core.designsystem.theme.LxAmberSoft
import com.lightxin.core.designsystem.theme.LxCategoryColors
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxPlum
import com.lightxin.core.designsystem.theme.LxPlumSoft
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxSageSoft
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxTerraSoft
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.labor.domain.HoursSummary
import com.lightxin.feature.running.domain.RunningDashboard
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.navigation.Routes
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale
import kotlin.math.max

// 日目标固定值（当前数据模型无专用字段；若后端后续提供，改读该字段）
private const val DAILY_TARGET_KM = 3.0

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
            GreetingSection(
                userName = uiState.userName,
                dashboardData = uiState.dashboardData,
            )

            Spacer(modifier = Modifier.height(22.dp))

            if (uiState.isLoading) {
                repeat(4) {
                    LxShimmerCard()
                    Spacer(modifier = Modifier.height(14.dp))
                }
            } else {
                val data = uiState.dashboardData
                val errors = uiState.errors

                StaggeredCard(index = 0) {
                    TodayCourseCard(
                        courses = data.todayCourses,
                        currentWeek = data.currentWeek,
                        error = errors["schedule"],
                        onClick = { onTabSelected(1) },
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                StaggeredCard(index = 1) {
                    CheckinCard(
                        task = data.nextCheckin,
                        error = errors["checkin"],
                        onClick = {
                            navController.navigate(Routes.CHECKIN_LIST) { launchSingleTop = true }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                StaggeredCard(index = 2) {
                    RunningCard(
                        dashboard = data.runningProgress,
                        error = errors["running"],
                        onClick = {
                            navController.navigate(Routes.RUNNING_HOME) { launchSingleTop = true }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                StaggeredCard(index = 3) {
                    LaborCard(
                        hours = data.laborHours,
                        error = errors["labor"],
                        onClick = {
                            navController.navigate(Routes.LABOR_SUMMARY) { launchSingleTop = true }
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════ 问候区 ═══════════════

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
        style = MaterialTheme.typography.headlineLarge, // Newsreader 30sp Medium
    )

    Spacer(modifier = Modifier.height(5.dp))

    val subtitle = remember(dashboardData, hour) {
        buildSmartSubtitle(dashboardData, hour)
    }

    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = LxInkMuted,
    )
}

private fun buildSmartSubtitle(data: HomeDashboardData, hour: Int): String {
    val isLateNight = hour in 0..5
    if (isLateNight) return "这么晚了还没睡？注意身体哦"
    if (hour >= 23 && data.tomorrowFirstSection != null && data.tomorrowFirstSection <= 2) {
        return "明天有早课，记得早点休息哦"
    }
    if (hour >= 19 && data.nextCheckin != null) return "别忘了完成查寝签到哦"
    if (hour >= 23) return "夜深了，早点休息"
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
    if (data.todayCourses.isNotEmpty()) {
        val lastSection = data.todayCourses.maxOf { it.endSection }
        val approximateEndHour = 8 + lastSection
        if (hour >= approximateEndHour) return "今天的课结束了，辛苦啦"
    }
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
private fun StaggeredCard(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 },
    ) { content() }
}

// ═══════════════ 今日课程 ═══════════════

@Composable
private fun TodayCourseCard(
    courses: List<Course>,
    currentWeek: Int,
    error: String?,
    onClick: () -> Unit,
) {
    DashboardCard(
        icon = Icons.Default.CalendarMonth,
        iconBg = LxTerraSoft,
        iconTint = LxTerra,
        title = "今日课程",
        badge = if (currentWeek > 0) "第${currentWeek}周" else null,
        onClick = onClick,
    ) {
        when {
            error != null -> ErrorHint(error)
            courses.isEmpty() -> EmptyHint("今天没有课，好好休息")
            else -> {
                courses.take(3).forEach { course ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = LxCategoryColors[
                            (course.name.hashCode() and 0x7FFFFFFF) % LxCategoryColors.size
                        ]
                        // 左侧 3dp × 36dp 彩色竖条
                        Box(
                            modifier = Modifier
                                .size(width = 3.dp, height = 36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 19.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "第${course.startSection}-${course.endSection}节 · ${course.room}",
                                fontSize = 12.sp,
                                color = LxInkMuted,
                            )
                        }
                    }
                }
                if (courses.size > 3) {
                    Text(
                        text = "还有 ${courses.size - 3} 节课...",
                        style = MaterialTheme.typography.labelSmall,
                        color = LxTerra,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════ 查寝签到 ═══════════════

@Composable
private fun CheckinCard(task: CheckinTask?, error: String?, onClick: () -> Unit) {
    DashboardCard(
        icon = Icons.Default.Bed,
        iconBg = LxAmberSoft,
        iconTint = LxAmber,
        title = "查寝签到",
        onClick = onClick,
    ) {
        when {
            error != null -> ErrorHint(error)
            task == null -> EmptyHint("暂无待签到任务")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(color = LxAmber)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = task.taskName.ifBlank { "查寝签到" },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = buildString {
                                append(task.startTime)
                                if (task.endTime.isNotBlank()) append(" ~ ${task.endTime}")
                            },
                            fontSize = 12.sp,
                            color = LxInkMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color) {
    val trans = rememberInfiniteTransition(label = "pulse")
    val a by trans.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(2_500), RepeatMode.Reverse),
        label = "pulseA",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(a)
            .clip(CircleShape)
            .background(color),
    )
}

// ═══════════════ 运动进度 ═══════════════

@Composable
private fun RunningCard(dashboard: RunningDashboard?, error: String?, onClick: () -> Unit) {
    DashboardCard(
        icon = Icons.Default.DirectionsRun,
        iconBg = LxSageSoft,
        iconTint = LxSage,
        title = "运动进度",
        onClick = onClick,
    ) {
        when {
            error != null -> ErrorHint(error)
            dashboard == null -> EmptyHint("暂无运动数据")
            else -> {
                // 今日环形进度
                val dailyProgress = (dashboard.todayKm / DAILY_TARGET_KM).coerceIn(0.0, 1.0).toFloat()
                val remaining = max(DAILY_TARGET_KM - dashboard.todayKm, 0.0)

                Row(
                    modifier = Modifier.defaultMinSize(minHeight = 54.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(54.dp),
                            strokeWidth = 4.5.dp,
                            color = LxSand,
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Round,
                        )
                        CircularProgressIndicator(
                            progress = { dailyProgress },
                            modifier = Modifier.size(54.dp),
                            strokeWidth = 4.5.dp,
                            color = LxSage,
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Round,
                        )
                        Text(
                            text = String.format(Locale.CHINA, "%.0f%%", dailyProgress * 100),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 13.sp,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 54.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "今日 ${formatKm(dashboard.todayKm)} km",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 19.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val subtitle = if (dashboard.todayKm >= DAILY_TARGET_KM) {
                            "今日目标已达成"
                        } else {
                            "目标 ${DAILY_TARGET_KM.toInt()} km · 离完成还差 ${formatKm(remaining)} km"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = LxInkMuted,
                            lineHeight = 16.sp,
                        )
                    }
                }

                // 总进度横条
                Spacer(modifier = Modifier.height(14.dp))
                TotalProgressBar(
                    completedKm = dashboard.completedKm,
                    targetKm = dashboard.taskTargetKm,
                )
            }
        }
    }
}

@Composable
private fun TotalProgressBar(completedKm: Double, targetKm: Double) {
    val fraction = if (targetKm > 0) {
        (completedKm / targetKm).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "总进度",
            fontSize = 12.sp,
            color = LxInkMuted,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(LxSand),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(LxSage),
            )
        }
        Text(
            text = "${formatKm(completedKm)} / ${formatKm(targetKm)} km",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

// ═══════════════ 劳动教育 ═══════════════

@Composable
private fun LaborCard(hours: HoursSummary?, error: String?, onClick: () -> Unit) {
    DashboardCard(
        icon = Icons.Default.WorkHistory,
        iconBg = LxPlumSoft,
        iconTint = LxPlum,
        title = "劳动教育",
        badge = hours?.let { "共 ${formatHours(it.totalTimes)} h" },
        onClick = onClick,
    ) {
        when {
            error != null -> ErrorHint(error)
            hours == null -> EmptyHint("暂无工时数据")
            else -> {
                val items = listOf(
                    "志愿" to hours.voluntaryTimes,
                    "暑期" to hours.summerTimes,
                    "劳动" to hours.laborTimes,
                    "社区" to hours.socialTimes,
                    "其他" to hours.otherTimes,
                )
                items.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = LxInkSoft,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${formatHours(value)} h",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                            textAlign = TextAlign.End,
                        )
                    }
                    if (index < items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(LxSand),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════ 通用卡片容器 ═══════════════

@Composable
private fun DashboardCard(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    badge: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 32×32 圆角 8dp 语义色方块
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium, // Newsreader 14pt Medium
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    BadgePill(text = badge)
                }
            }
            content()
        }
    }
}

@Composable
private fun BadgePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(LxTerraSoft)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = LxTerra,
            letterSpacing = 0.6.sp,
        )
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

@Composable
private fun EmptyHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = LxInkMuted,
    )
}

private fun formatKm(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.CHINA, "%.0f", value)
    else String.format(Locale.CHINA, "%.2f", value)

private fun formatHours(value: Double): String =
    String.format(Locale.CHINA, "%.1f", value)
