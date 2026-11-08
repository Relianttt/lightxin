package com.lightxin.feature.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
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
import com.lightxin.feature.schedule.data.local.SCHOOL_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.LocalDate

private const val SECTION_COUNT = 10
private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val CELL_HEIGHT = 58.dp
private val SECTION_LABEL_WIDTH = 32.dp
private val GRID_START_PADDING = 12.dp
private val GRID_END_PADDING = 10.dp
private val BLOCK_VERTICAL_PADDING = 3.dp
private val NAME_FONT_SIZE = 10.sp
private val NAME_LINE_HEIGHT = 13.sp
private val ROOM_FONT_SIZE = 9.sp
private val ROOM_LINE_HEIGHT = 11.sp

/** 选中胶囊在两周之间被拉到最长时，上下各收窄这么多，看起来像被扯细了一点。 */
private val INDICATOR_SQUASH = 2.dp

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
    val info = uiState.weekInfo

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (info == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    uiState.error?.let { LxError(message = it, onRetry = viewModel::retry) } ?: LxLoading()
                }
            } else {
                val weeks = info.weekNumbers
                val pagerState = rememberPagerState(
                    initialPage = weeks.indexOf(uiState.selectedWeek).coerceAtLeast(0),
                ) { weeks.size }
                SyncPagerWithSelection(pagerState, weeks, uiState.selectedWeek, viewModel::onWeekSelected)

                WeekSelector(
                    weeks = weeks,
                    selectedWeek = uiState.selectedWeek,
                    currentWeek = info.currentWeek,
                    position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    onWeekSelected = viewModel::onWeekSelected,
                )

                // 同步时间只在刷新失败、正在用本地缓存时提示；联网正常时不占版面。
                uiState.lastSyncedAt?.takeIf { uiState.refreshError != null }?.let { syncedAt ->
                    val time = DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(SCHOOL_ZONE).format(Instant.ofEpochMilli(syncedAt))
                    Text("正在显示本地课表 · 上次同步 $time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }

                // 每周一页平铺，拖动时能看到相邻周跟着滑进来；纵向滚动放在翻页器外层，
                // 各周共用一个滚动位置——看着下午的课翻页，下一周也停在下午。
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val viewportHeight = maxHeight
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        beyondViewportPageCount = 1,
                        key = { weeks[it] },
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        val week = weeks[page]
                        val data = uiState.weekData[week]
                        if (data != null && data.courses.isNotEmpty()) {
                            ScheduleGrid(
                                courses = data.courses,
                                weekDates = data.weekDates,
                                showCurrentDayIndicator = week == info.currentWeek,
                                onCourseClick = { selectedCourse = it },
                            )
                        } else {
                            // 外层是纵向滚动（高度不设上限），占位态给满一屏高度才能居中
                            Box(modifier = Modifier.fillMaxWidth().height(viewportHeight)) {
                                val error = uiState.error
                                when {
                                    data != null -> LxEmpty(message = "本周没有课程")
                                    week == uiState.selectedWeek && error != null ->
                                        LxError(message = error, onRetry = viewModel::retry)
                                    else -> LxLoading()
                                }
                            }
                        }
                    }
                }
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

/**
 * 翻页器与 ViewModel 选中周双向对齐：
 * - 选中周从别处变了（点胶囊 / 点「本周」/ 换学期）→ 翻页器滑过去；
 * - 手指翻页停稳 → 告诉 ViewModel 选中那一周。
 * 两边各自先比对一下，已经一致就不动，避免来回触发。
 */
@Composable
private fun SyncPagerWithSelection(
    pagerState: PagerState,
    weeks: List<Int>,
    selectedWeek: Int,
    onWeekSelected: (Int) -> Unit,
) {
    LaunchedEffect(pagerState, selectedWeek, weeks) {
        val index = weeks.indexOf(selectedWeek)
        if (index >= 0 && index != pagerState.currentPage) pagerState.animateScrollToPage(index)
    }
    val currentSelected by rememberUpdatedState(selectedWeek)
    val currentOnWeekSelected by rememberUpdatedState(onWeekSelected)
    LaunchedEffect(pagerState, weeks) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            weeks.getOrNull(page)?.takeIf { it != currentSelected }?.let(currentOnWeekSelected)
        }
    }
}

@Composable
private fun WeekSelector(
    weeks: List<Int>,
    selectedWeek: Int,
    currentWeek: Int,
    /** 翻页器的连续位置（页序号 + 拖动偏移），选中胶囊按它逐帧移动。 */
    position: () -> Float,
    onWeekSelected: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // 首次直接落到选中周，之后切周才滚动过去
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(selectedWeek, weeks) {
        val index = weeks.indexOf(selectedWeek)
        if (index < 0) return@LaunchedEffect
        val target = (index - 2).coerceAtLeast(0)
        if (positioned) listState.animateScrollToItem(target) else listState.scrollToItem(target)
        positioned = true
    }
    val currentWeekHint by remember(listState, currentWeek, weeks) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null
            val firstVisibleIndex = visibleItems.first().index
            val lastVisibleIndex = visibleItems.last().index
            val currentIndex = weeks.indexOf(currentWeek)
            when {
                currentIndex < 0 -> null
                currentIndex < firstVisibleIndex -> "← 本周" to Alignment.TopStart
                currentIndex > lastVisibleIndex -> "本周 →" to Alignment.TopEnd
                else -> null
            }
        }
    }
    val currentPosition by rememberUpdatedState(position)
    val indicatorColor = LxTerra

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
                // 选中胶囊画在所有周次文字底下，只在绘制阶段读位置，拖动时不触发重组
                .drawBehind { drawWeekIndicator(listState, currentPosition(), weeks.lastIndex, indicatorColor) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(weeks) { index, week ->
                val emphasis by remember(index) {
                    derivedStateOf { chipEmphasis(currentPosition(), index) }
                }
                WeekChip(
                    label = if (week == 0) "预备" else "第${week}周",
                    isCurrent = week == currentWeek,
                    emphasis = emphasis,
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
                    // clickable 放在 padding 外侧，把内边距也算进“点回本周”的点击区
                    .clickable { onWeekSelected(currentWeek) }
                    .padding(start = 20.dp, end = 20.dp, top = 2.dp),
                fontSize = 12.sp,
                color = LxInkMuted,
            )
        }
    }
}

/** 按翻页位置画选中胶囊：停稳时贴合某一枚周次，拖动途中在相邻两枚之间拉长、变细。 */
private fun DrawScope.drawWeekIndicator(
    listState: LazyListState,
    position: Float,
    lastIndex: Int,
    color: Color,
) {
    if (lastIndex < 0) return
    val clamped = position.coerceIn(0f, lastIndex.toFloat())
    val from = clamped.toInt()
    val fraction = clamped - from
    val items = listState.layoutInfo.visibleItemsInfo
    fun spanOf(index: Int) = items.firstOrNull { it.index == index }
        ?.let { IndicatorSpan(it.offset.toFloat(), (it.offset + it.size).toFloat()) }
    val a = spanOf(from)
    val b = if (fraction > 0f) spanOf(from + 1) else a
    // 跨很多周跳转时两端可能有一端滚出可视区，退化为贴着看得见的那枚
    val span = if (a != null && b != null) stretchedSpan(a, b, fraction) else a ?: b ?: return
    val inset = stretchAmount(fraction) * INDICATOR_SQUASH.toPx()
    val height = size.height - inset * 2
    drawRoundRect(
        color = color,
        topLeft = Offset(span.start, inset),
        size = Size(span.end - span.start, height),
        cornerRadius = CornerRadius(height / 2),
    )
}

@Composable
private fun WeekChip(
    label: String,
    isCurrent: Boolean,
    /** 被选中胶囊覆盖的程度：0 = 未选中，1 = 正中选中；拖动途中连续变化。 */
    emphasis: Float,
    onClick: () -> Unit,
) {
    // 底色由 WeekSelector 统一画（会移动、会变形），这里只管文字和“本周”描边随覆盖程度渐变。
    // 贴在陶土品牌色块上的前景：Light/Dark 两种主题下品牌色都是暖橙，固定白字对比度最佳
    val textColor = lerp(if (isCurrent) LxInkSoft else LxInkMuted, Color.White, emphasis)
    val borderColor = if (isCurrent) LxInkMuted.copy(alpha = LxInkMuted.alpha * (1f - emphasis)) else Color.Transparent
    // 字重只随“本周”变、不随选中变：否则拖到一半胶囊宽度会跳一下
    val weight = if (isCurrent) FontWeight.Medium else FontWeight.Normal

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
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
    // 大一大二接口会下发第0节（早自习），存在时网格从第0节起画；大三等无第0节则从第1节起，行为不变
    val firstSection = if (courses.any { it.startSection <= 0 }) 0 else 1
    val rowCount = SECTION_COUNT - firstSection + 1
    val density = LocalDensity.current
    // sp→dp 会带上系统字体缩放，行数预算才能跟实际排版对齐
    val nameLineDp = with(density) { NAME_LINE_HEIGHT.toDp().value }
    val roomLineDp = with(density) { ROOM_LINE_HEIGHT.toDp().value }

    // 纵向滚动由外层翻页器统一负责，这里只按内容撑高
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                repeat(rowCount) { row ->
                    val section = firstSection + row
                    Box(
                        modifier = Modifier.height(CELL_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (section == 0) "早" else "$section",
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
                        .height(CELL_HEIGHT * rowCount)
                        .then(
                            if (isToday) Modifier.background(LxTerraGlow) else Modifier
                        ),
                ) {
                    // 课程块（绝对定位）
                    dayCourses.forEach { course ->
                        val rows = course.endSection - course.startSection + 1
                        val top = (course.startSection - firstSection) * CELL_HEIGHT.value
                        val height = rows * CELL_HEIGHT.value
                        val color = courseColor(course.name)
                        val hasRoom = course.room.isNotBlank()
                        val roomLabel = shortRoomLabel(course.room)
                        val (nameLines, roomLines) = courseBlockLines(
                            innerHeightDp = height - BLOCK_VERTICAL_PADDING.value * 2,
                            nameLineDp = nameLineDp,
                            roomLineDp = roomLineDp,
                            hasRoom = hasRoom,
                        )

                        Box(
                            modifier = Modifier
                                .padding(top = top.dp, start = 1.dp, end = 1.dp)
                                .fillMaxWidth()
                                .height(height.dp)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color)
                                .clickable { onCourseClick(course) }
                                .padding(horizontal = 5.dp, vertical = BLOCK_VERTICAL_PADDING),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Column {
                                Text(
                                    text = course.name,
                                    fontSize = NAME_FONT_SIZE,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = NAME_LINE_HEIGHT,
                                    // 贴在课程块分类色底上的前景固定白字，不随主题变
                                    color = Color.White,
                                    maxLines = nameLines,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (hasRoom) {
                                    Text(
                                        text = roomLabel,
                                        fontSize = ROOM_FONT_SIZE,
                                        lineHeight = ROOM_LINE_HEIGHT,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = roomLines,
                                        overflow = TextOverflow.Ellipsis,
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
