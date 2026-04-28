package com.lightxin.feature.holiday.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxTerraSoft
import com.lightxin.core.designsystem.theme.NewsreaderLarge
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ═══════════════ 两步日期时间选择 ═══════════════

/**
 * 日期时间选择状态：管理两步流程
 */
class HolidayDatePickerState {
    /** 当前显示的月份 */
    var viewingMonth by mutableStateOf(YearMonth.now())
        private set

    /** 第一步：选日期时暂存 */
    var pendingDate by mutableStateOf<LocalDate?>(null)
        private set

    /** 第二步：时分默认值（由时间列直接修改） */
    var pendingHour by mutableStateOf(12)
    var pendingMinute by mutableStateOf(0)

    /** 是否显示日历 sheet */
    var showCalendar by mutableStateOf(false)
        private set

    /** 是否显示时间 sheet */
    var showTime by mutableStateOf(false)
        private set

    /** 当前编辑的是哪个字段索引（0=开始, 1=结束） */
    var editingField by mutableStateOf(0)
        private set

    /** 格式化后的结果 */
    var result by mutableStateOf<String?>(null)
        private set

    fun open(fieldIndex: Int, currentValue: String) {
        editingField = fieldIndex
        result = null

        // 解析已有值作为默认
        val parsed = runCatching {
            LocalDateTime.parse(currentValue, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrNull()

        if (parsed != null) {
            pendingDate = parsed.toLocalDate()
            pendingHour = parsed.hour
            pendingMinute = parsed.minute
        } else {
            pendingDate = LocalDate.now()
            pendingHour = 12
            pendingMinute = 0
        }

        viewingMonth = YearMonth.from(pendingDate)
        showCalendar = true
    }

    fun selectDay(day: LocalDate) {
        pendingDate = day
        showCalendar = false
        showTime = true
    }

    fun confirmTime() {
        val date = pendingDate ?: return
        val dt = LocalDateTime.of(date, LocalTime.of(pendingHour, pendingMinute))
        result = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        showTime = false
        pendingDate = null
    }

    fun dismissAll() {
        showCalendar = false
        showTime = false
        pendingDate = null
    }

    fun navigateMonth(delta: Int) {
        viewingMonth = viewingMonth.plusMonths(delta.toLong())
    }
}

@Composable
fun rememberHolidayDatePickerState(): HolidayDatePickerState =
    remember { HolidayDatePickerState() }

// ═══════════════ 日历 BottomSheet ═══════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayCalendarSheet(
    state: HolidayDatePickerState,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (!state.showCalendar) return

    ModalBottomSheet(
        onDismissRequest = { state.dismissAll() },
        sheetState = sheetState,
        containerColor = LxSand,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LxTerra.copy(alpha = 0.25f)),
                )
            }
        },
    ) {
        CalendarContent(state = state)
    }
}

@Composable
private fun CalendarContent(state: HolidayDatePickerState) {
    val month = state.viewingMonth
    val today = remember { LocalDate.now() }
    val selected = state.pendingDate

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        // ── 月份导航 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "◀",
                fontSize = 16.sp,
                color = LxInkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { state.navigateMonth(-1) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                text = "${month.year}年${month.monthValue}月",
                fontFamily = NewsreaderLarge,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = LxInk,
            )
            Text(
                text = "▶",
                fontSize = 16.sp,
                color = LxInkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { state.navigateMonth(1) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 星期头 ──
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = LxTerra,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 日期网格 ──
        val firstDay = month.atDay(1)
        // 周一 = 1, 周日 = 7; 偏移量让周一排第一列
        val startOffset = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val index = row * 7 + col
                    val dayNum = index - startOffset + 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            val isToday = date == today
                            val isSelected = date == selected

                            DayCell(
                                day = dayNum,
                                isToday = isToday,
                                isSelected = isSelected,
                                onClick = { state.selectDay(date) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val textColor = LxInk

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(LxTerraSoft, CircleShape) else Modifier)
            .then(
                if (isToday && !isSelected) {
                    Modifier.background(Color.Transparent, CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 今天外圈
        if (isToday && !isSelected) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(LxTerraSoft),
            )
        }

        Text(
            text = day.toString(),
            fontSize = 14.sp,
            fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

// ═══════════════ 时间 BottomSheet ═══════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayTimeSheet(
    state: HolidayDatePickerState,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (!state.showTime || state.pendingDate == null) return

    ModalBottomSheet(
        onDismissRequest = { state.dismissAll() },
        sheetState = sheetState,
        containerColor = LxSand,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LxTerra.copy(alpha = 0.25f)),
                )
            }
        },
    ) {
        TimeContent(state = state)
    }
}

@Composable
private fun TimeContent(state: HolidayDatePickerState) {
    val date = state.pendingDate ?: return
    val dateLabel = remember(date) {
        "${date.monthValue}月${date.dayOfMonth}日"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        // ── 已选日期 ──
        Text(
            text = dateLabel,
            fontFamily = NewsreaderLarge,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            color = LxInk,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 陶土横线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LxTerra.copy(alpha = 0.18f)),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 时 / 分 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            // 时列
            TimeColumn(
                values = (0..23).toList(),
                selected = state.pendingHour,
                onSelect = { state.pendingHour = it },
                modifier = Modifier.weight(1f),
            )

            Text(
                text = ":",
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                color = LxInkMuted,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 8.dp),
            )

            // 分列
            TimeColumn(
                values = (0..59).toList(),
                selected = state.pendingMinute,
                onSelect = { state.pendingMinute = it },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 确认按钮 ──
        Button(
            onClick = { state.confirmTime() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = LxTerra,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = "确认",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TimeColumn(
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = values.indexOf(selected)

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(
            index = (selectedIndex - 1).coerceIn(0, values.lastIndex),
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部留白让选中项大致居中
        item(key = "pad_top") { Spacer(modifier = Modifier.height(64.dp)) }

        itemsIndexed(values, key = { _, v -> "t_$v" }) { _, value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) Modifier.background(LxTerraSoft, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = String.format("%02d", value),
                    fontSize = if (isSelected) 22.sp else 18.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = LxInk,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }

        item(key = "pad_bottom") { Spacer(modifier = Modifier.height(64.dp)) }
    }
}
