package com.lightxin.feature.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxShimmerCard
import com.lightxin.core.designsystem.theme.LxAmber
import com.lightxin.core.designsystem.theme.LxCategoryColors
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.NewsreaderLarge
import com.lightxin.feature.checkin.domain.CheckinTask
import com.lightxin.feature.home.domain.SectionSchedule
import com.lightxin.feature.running.domain.RunningDashboard
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.navigation.Routes
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale
import kotlin.math.max

// 日目标固定值（当前数据模型无专用字段；若后端后续提供，改读该字段）
private const val DAILY_TARGET_KM = 3.0

// 查寝签到条件渲染窗口：开始前 4 小时内才出现在首页
private const val CHECKIN_VISIBLE_HOURS_BEFORE = 4

// 副标题 / 场景每分钟重算一次，驱动「还有 12 分钟上课」等分钟级文案
private const val SUBTITLE_TICK_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController,
    onTabSelected: (Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    // 生命周期：ON_PAUSE 记录时间戳，ON_RESUME 触发副标题轮换判定 + 重算场景
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPaused() }

    // 前台定时器：每分钟重算副标题，驱动分钟级场景文案
    LaunchedEffect(Unit) {
        while (true) {
            delay(SUBTITLE_TICK_MS)
            viewModel.recomputeSubtitle()
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 16.dp), // 状态栏与页标题之间 24dp
        ) {
            // ── 问候 + 副标 ──
            GreetingSection(
                userName = uiState.userName,
                subtitle = uiState.subtitle,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(modifier = Modifier.height(22.dp))

            // ── 陶土横线（叙事切换：问候 → 下一节）──
            SectionRule()

            Spacer(modifier = Modifier.height(22.dp))

            if (uiState.isLoading) {
                // "下一节" headline 占位
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "正在加载…",
                        fontFamily = NewsreaderLarge,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        color = LxInkMuted,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    repeat(2) { LxShimmerCard() }
                }
            } else {
                val data = uiState.dashboardData
                val errors = uiState.errors

                // ── "下一节" headline（非卡片，叙事主角）──
                StaggeredCard(index = 0) {
                    NextClassHeadline(
                        courses = data.todayCourses,
                        tomorrowFirstSection = data.tomorrowFirstSection,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StaggeredCard(index = 1) {
                        TodayCourseCard(
                            courses = data.todayCourses,
                            currentWeek = data.currentWeek,
                            error = errors["schedule"],
                            onClick = { onTabSelected(1) },
                        )
                    }

                    StaggeredCard(index = 2) {
                        RunningCard(
                            dashboard = data.runningProgress,
                            error = errors["running"],
                            onClick = {
                                navController.navigate(Routes.RUNNING_HOME) { launchSingleTop = true }
                            },
                        )
                    }

                    if (shouldShowCheckin(data.nextCheckin)) {
                        StaggeredCard(index = 3) {
                            CheckinCard(
                                task = data.nextCheckin!!,
                                onClick = {
                                    navController.navigate(Routes.CHECKIN_LIST) { launchSingleTop = true }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════ 问候区 ═══════════════

@Composable
private fun GreetingSection(
    userName: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val hour = remember { LocalTime.now().hour }
    val greeting = remember(hour) {
        when (hour) {
            in 0..5 -> "夜深了"
            in 6..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
    }

    Column(modifier = modifier) {
        // 陶土衬线主标（陶土色用法 1/2）
        Text(
            text = if (userName.isNotBlank()) "$greeting，$userName" else greeting,
            style = MaterialTheme.typography.headlineLarge,
            color = LxTerra,
        )

        Spacer(modifier = Modifier.height(5.dp))

        // 副标：衬线 italic + 墨灰；切换时旧文字向上淡出，新文字从下方淡入（文档 3.4）
        AnimatedContent(
            targetState = subtitle,
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 200)) +
                    slideInVertically(tween(200, delayMillis = 200)) { it / 2 })
                    .togetherWith(
                        fadeOut(tween(200)) +
                            slideOutVertically(tween(200)) { -it / 2 },
                    )
            },
            label = "subtitle-switch",
        ) { text ->
            Text(
                text = text,
                fontFamily = NewsreaderLarge,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = LxInkMuted,
            )
        }
    }
}

// ═══════════════ 陶土横线 ═══════════════

@Composable
private fun SectionRule() {
    // 陶土色用法 2/2：1px 陶土 0.18 透明，与文本同水平（左右 24dp）
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(1.dp)
            .background(LxTerra.copy(alpha = 0.18f)),
    )
}

// ═══════════════ "下一节" headline ═══════════════

@Composable
private fun NextClassHeadline(
    courses: List<Course>,
    tomorrowFirstSection: Int?,
    modifier: Modifier = Modifier,
) {
    val now = remember { LocalTime.now() }
    val headline = remember(courses, tomorrowFirstSection, now) {
        buildNextClassHeadline(courses, tomorrowFirstSection, now)
    }
    Text(
        text = headline,
        modifier = modifier,
        fontFamily = NewsreaderLarge,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        color = LxInk,
    )
}

private fun buildNextClassHeadline(
    courses: List<Course>,
    tomorrowFirstSection: Int?,
    now: LocalTime,
): String {
    if (courses.isEmpty()) {
        return if (tomorrowFirstSection != null) {
            "明天第 $tomorrowFirstSection 节开课"
        } else {
            "今天没有课程安排"
        }
    }
    val current = courses.firstOrNull { c ->
        val s = SectionSchedule.startOf(c.startSection) ?: return@firstOrNull false
        val e = SectionSchedule.endOf(c.endSection) ?: return@firstOrNull false
        now >= s && now < e
    }
    if (current != null) {
        return "正在上 · ${current.name} · ${current.room}"
    }
    val next = courses.firstOrNull { c ->
        val s = SectionSchedule.startOf(c.startSection) ?: return@firstOrNull false
        now < s
    }
    if (next != null) {
        val start = SectionSchedule.startOf(next.startSection)
        val clock = if (start != null) {
            String.format(Locale.CHINA, "%02d:%02d", start.hour, start.minute)
        } else {
            "第 ${next.startSection} 节"
        }
        return "下一节 · ${next.name} · ${next.room} · $clock"
    }
    return if (tomorrowFirstSection != null) {
        "明天第 $tomorrowFirstSection 节开课"
    } else {
        "今天的课全部结束"
    }
}

// ═══════════════ 通用 stagger 动画 ═══════════════

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
    // badge 改为承载"星期"（课数在列表里可见，不重复）
    val weekdayLabel = remember {
        val idx = java.time.LocalDate.now().dayOfWeek.value - 1
        listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[idx]
    }
    DashboardCard(
        title = "今日课程",
        badge = if (currentWeek > 0) "第 $currentWeek 周 · $weekdayLabel" else weekdayLabel,
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
                        // 唯一保留的彩色：左侧 3dp 竖条（与课表页块色联动，维持"分类"语义）
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
                        color = LxInkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════ 查寝签到（条件渲染）═══════════════

private fun shouldShowCheckin(task: CheckinTask?): Boolean {
    if (task == null) return false
    val start = parseHourMinute(task.startTime) ?: return false
    val now = LocalTime.now()
    val nowMin = now.hour * 60 + now.minute
    val startMin = start.first * 60 + start.second
    val diffMin = startMin - nowMin
    // 开始前 4 小时内 OR 已在窗口内（start..end 之间即 diff <= 0 但未过期）
    return diffMin in -(CHECKIN_VISIBLE_HOURS_BEFORE * 60)..(CHECKIN_VISIBLE_HOURS_BEFORE * 60)
}

private fun parseHourMinute(raw: String): Pair<Int, Int>? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":", "：").mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return null
    return parts[0] to parts[1]
}

@Composable
private fun CheckinCard(task: CheckinTask, onClick: () -> Unit) {
    DashboardCard(
        title = "查寝签到",
        onClick = onClick,
    ) {
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
    // badge 承载互补信息（body 说"离完成还差 X"，badge 说"目标 3 km"）
    val targetBadge = "目标 ${DAILY_TARGET_KM.toInt()} km"
    DashboardCard(
        title = "运动进度",
        badge = targetBadge,
        onClick = onClick,
    ) {
        when {
            error != null -> ErrorHint(error)
            dashboard == null -> EmptyHint("暂无运动数据")
            else -> {
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
                            "离完成还差 ${formatKm(remaining)} km"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = LxInkMuted,
                            lineHeight = 16.sp,
                        )
                    }
                }
                // 总进度横条删除 — 每张卡只承载一个核心信息；学期进度移至运动详情页
            }
        }
    }
}

// ═══════════════ 通用卡片容器 ═══════════════

@Composable
private fun DashboardCard(
    title: String,
    badge: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            // 卡头去图标色块：仅 [serif 标题] ──── [墨灰 badge]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    BadgeText(text = badge)
                }
            }
            content()
        }
    }
}

/** 纯 12sp 墨灰 sans 小字 — 去掉胶囊背景，只承载卡片内没有的信息 */
@Composable
private fun BadgeText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = LxInkMuted,
    )
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
