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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.feature.schedule.domain.Course
import java.time.LocalDate

private val SECTION_COUNT = 10
private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val CELL_HEIGHT = 64.dp
private val CELL_WIDTH = 52.dp
private val SECTION_LABEL_WIDTH = 28.dp

// 柔和的课程色板
private val courseColors = listOf(
    Color(0xFF5B7FD3), // 蓝
    Color(0xFFE8734A), // 橙
    Color(0xFF4CAF7A), // 绿
    Color(0xFFAB6FD1), // 紫
    Color(0xFFE0A145), // 黄
    Color(0xFF49A7C4), // 青
    Color(0xFFD36B7E), // 粉红
    Color(0xFF7B8FA0), // 灰蓝
)

private fun courseColor(name: String): Color {
    val index = (name.hashCode() and 0x7FFFFFFF) % courseColors.size
    return courseColors[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 周次选择器
            uiState.weekInfo?.let { info ->
                WeekSelector(
                    totalWeeks = info.totalWeeks,
                    selectedWeek = uiState.selectedWeek,
                    currentWeek = info.currentWeek,
                    onWeekSelected = viewModel::onWeekSelected,
                )
            }

            // 内容区域
            when {
                uiState.isLoading -> LxLoading()
                uiState.error != null -> LxError(
                    message = uiState.error!!,
                    onRetry = viewModel::retry,
                )
                uiState.courses.isEmpty() -> LxEmpty(message = "本周没有课程")
                else -> ScheduleGrid(
                    courses = uiState.courses,
                    onCourseClick = { selectedCourse = it },
                )
            }
        }
    }

    // 课程详情 BottomSheet
    if (selectedCourse != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedCourse = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CourseDetail(course = selectedCourse!!)
        }
    }
}

@Composable
private fun WeekSelector(
    totalWeeks: Int,
    selectedWeek: Int,
    currentWeek: Int,
    onWeekSelected: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // 初始滚动到选中周
    LaunchedEffect(selectedWeek) {
        val target = (selectedWeek - 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(List(totalWeeks) { it }) { _, week ->
            val isSelected = week == selectedWeek
            val isCurrent = week == currentWeek
            val label = if (week == 0) "预备" else "第${week}周"

            FilterChip(
                selected = isSelected,
                onClick = { onWeekSelected(week) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun ScheduleGrid(
    courses: List<Course>,
    onCourseClick: (Course) -> Unit,
) {
    val today = remember { LocalDate.now().dayOfWeek.value } // 1=周一

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 表头：星期
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左上角空格
            Box(modifier = Modifier.width(SECTION_LABEL_WIDTH))

            DAY_LABELS.forEachIndexed { index, label ->
                val dayIndex = index + 1
                val isToday = dayIndex == today
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        // 课程网格
        Row(modifier = Modifier.fillMaxWidth()) {
            // 节次标签列
            Column(modifier = Modifier.width(SECTION_LABEL_WIDTH)) {
                repeat(SECTION_COUNT) { section ->
                    Box(
                        modifier = Modifier.height(CELL_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${section + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            // 7 天的列
            (1..7).forEach { day ->
                val isToday = day == today
                val dayCourses = courses.filter { it.dayOfWeek == day }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(CELL_HEIGHT * SECTION_COUNT)
                        .then(
                            if (isToday) Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ) else Modifier
                        ),
                ) {
                    // 网格线
                    Column {
                        repeat(SECTION_COUNT) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(CELL_HEIGHT)
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    ),
                            )
                        }
                    }

                    // 课程卡片（绝对定位）
                    dayCourses.forEach { course ->
                        val top = (course.startSection - 1) * CELL_HEIGHT.value
                        val height = (course.endSection - course.startSection + 1) * CELL_HEIGHT.value

                        Box(
                            modifier = Modifier
                                .padding(start = 1.dp, end = 1.dp, top = top.dp)
                                .fillMaxWidth()
                                .height(height.dp)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(courseColor(course.name).copy(alpha = 0.15f))
                                .clickable { onCourseClick(course) }
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Column {
                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = courseColor(course.name),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                )
                                if (course.room.isNotBlank()) {
                                    Text(
                                        text = course.room,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = courseColor(course.name).copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 9.sp,
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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("时间", "星期${DAY_LABELS[course.dayOfWeek - 1]}  第${course.startSection}-${course.endSection}节")

        if (course.room.isNotBlank()) {
            DetailRow("教室", course.room)
        }

        if (course.teacher.isNotBlank()) {
            DetailRow("教师", course.teacher)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
