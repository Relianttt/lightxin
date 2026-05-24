package com.lightxin.feature.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxDetailRow
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.theme.LxCategoryColors
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxTerraGlow
import com.lightxin.feature.schedule.domain.Course
import java.time.LocalDate

private const val SECTION_COUNT = 10
private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val CELL_HEIGHT = 58.dp
private val SECTION_LABEL_WIDTH = 32.dp
private val GRID_START_PADDING = 12.dp
private val GRID_END_PADDING = 10.dp

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun courseColor(name: String): Color {
    val index = (name.hashCode() and 0x7FFFFFFF) % LxCategoryColors.size
    return LxCategoryColors[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    val error = uiState.error

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            uiState.weekInfo?.let { info ->
                WeekSelector(
                    totalWeeks = info.totalWeeks,
                    selectedWeek = uiState.selectedWeek,
                    currentWeek = info.currentWeek,
                    onWeekSelected = viewModel::onWeekSelected,
                )
            }

            when {
                uiState.isLoading -> LxLoading()
                error != null -> LxError(
                    message = error,
                    onRetry = viewModel::retry,
                )
                uiState.courses.isEmpty() -> LxEmpty(message = "本周没有课程")
                else -> ScheduleGrid(
                    courses = uiState.courses,
                    weekDates = uiState.weekDates,
                    showCurrentDayIndicator = uiState.weekInfo?.currentWeek == uiState.selectedWeek,
                    onCourseClick = { selectedCourse = it },
                )
            }
        }
    }

    selectedCourse?.let { course ->
        ModalBottomSheet(
            onDismissRequest = { selectedCourse = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            CourseDetail(course = course)
        }
    }
}

// ═══════════════ 周次选择器（三态胶囊） ═══════════════

@Composable
private fun WeekSelector(
    totalWeeks: Int,
    selectedWeek: Int,
    currentWeek: Int,
    onWeekSelected: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedWeek) {
        val target = (selectedWeek - 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }
    val currentWeekHint by remember(listState, currentWeek) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null
            val firstVisibleIndex = visibleItems.first().index
            val lastVisibleIndex = visibleItems.last().index
            when {
                currentWeek < firstVisibleIndex -> "← 本周" to Alignment.TopStart
                currentWeek > lastVisibleIndex -> "本周 →" to Alignment.TopEnd
                else -> null
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(List(totalWeeks) { it }) { _, week ->
                WeekChip(
                    label = if (week == 0) "预备" else "第${week}周",
                    isSelected = week == selectedWeek,
                    isCurrent = week == currentWeek,
                    onClick = { onWeekSelected(week) },
                )
            }
        }

        currentWeekHint?.let { (label, alignment) ->
            Text(
                text = label,
                modifier = Modifier
                    .align(alignment)
                    .offset(y = (-35).dp)
                    .padding(start = 20.dp, end = 20.dp, top = 2.dp),
                fontSize = 12.sp,
                color = LxInkMuted,
            )
        }
    }
}

@Composable
private fun WeekChip(
    label: String,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    // 四态组合：default / cur / on / on.cur（选中填充优先，选中时边框 transparent）
    val bg = if (isSelected) LxTerra else Color.Transparent
    val textColor = when {
        // 贴在陶土品牌色块上的前景：Light/Dark 两种主题下品牌色都是暖橙，固定白字对比度最佳
        isSelected -> Color.White
        isCurrent -> LxInkSoft
        else -> LxInkMuted
    }
    val borderColor = when {
        isSelected -> Color.Transparent
        isCurrent -> LxInkMuted
        else -> Color.Transparent
    }
    val weight = if (isCurrent || isSelected) FontWeight.Medium else FontWeight.Normal

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = weight,
            color = textColor,
        )
    }
}

// ═══════════════ 课表网格 ═══════════════

@Composable
private fun ScheduleGrid(
    courses: List<Course>,
    weekDates: Map<Int, String>,
    showCurrentDayIndicator: Boolean,
    onCourseClick: (Course) -> Unit,
) {
    val today = remember { LocalDate.now().dayOfWeek.value } // 1=周一

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = GRID_START_PADDING, end = GRID_END_PADDING),
    ) {
        // ── 表头：星期 ──
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(SECTION_LABEL_WIDTH))
            DAY_LABELS.forEachIndexed { index, label ->
                val dayIndex = index + 1
                val isToday = showCurrentDayIndicator && dayIndex == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) LxTerra else LxInkMuted,
                    )
                    weekDates[dayIndex]?.let { date ->
                        Text(
                            text = date,
                            fontSize = 10.sp,
                            color = LxInkMuted,
                        )
                    }
                    if (isToday) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(LxTerra),
                        )
                    }
                }
            }
        }

        // ── 课程网格 ──
        Row(modifier = Modifier.fillMaxWidth()) {
            // 节次列
            Column(modifier = Modifier.width(SECTION_LABEL_WIDTH)) {
                repeat(SECTION_COUNT) { section ->
                    Box(
                        modifier = Modifier.height(CELL_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${section + 1}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 7 天列
            (1..7).forEach { day ->
                val isToday = showCurrentDayIndicator && day == today
                val dayCourses = courses.filter { it.dayOfWeek == day }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(CELL_HEIGHT * SECTION_COUNT)
                        .then(
                            if (isToday) Modifier.background(LxTerraGlow) else Modifier
                        ),
                ) {
                    // 课程块（绝对定位）
                    dayCourses.forEach { course ->
                        val top = (course.startSection - 1) * CELL_HEIGHT.value
                        val height = (course.endSection - course.startSection + 1) * CELL_HEIGHT.value
                        val color = courseColor(course.name)

                        Box(
                            modifier = Modifier
                                .padding(top = top.dp, start = 1.dp, end = 1.dp)
                                .fillMaxWidth()
                                .height(height.dp)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color)
                                .clickable { onCourseClick(course) }
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Column {
                                Text(
                                    text = course.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 12.5.sp,
                                    // 贴在课程块分类色底上的前景固定白字，不随主题变
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (course.room.isNotBlank()) {
                                    Text(
                                        text = course.room,
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════ 课程详情 ═══════════════

@Composable
private fun CourseDetail(course: Course) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = course.name,
            style = MaterialTheme.typography.titleLarge, // Newsreader 24pt
            color = LxInk,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LxDetailRow(
            label = "时间",
            value = "星期${DAY_LABELS[course.dayOfWeek - 1]}  第${course.startSection}-${course.endSection}节",
            labelWidth = 48.dp,
            showDivider = false,
        )

        if (course.room.isNotBlank()) {
            LxDetailRow(label = "教室", value = course.room, labelWidth = 48.dp, showDivider = false)
        }

        if (course.teacher.isNotBlank()) {
            LxDetailRow(label = "教师", value = course.teacher, labelWidth = 48.dp, showDivider = false)
        }
    }
}
