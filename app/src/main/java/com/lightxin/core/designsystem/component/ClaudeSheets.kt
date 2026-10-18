package com.lightxin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxAccentText
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxHairline
import com.lightxin.core.designsystem.theme.LxOnAccent
import com.lightxin.core.designsystem.theme.LxRadiusSm
import com.lightxin.core.designsystem.theme.LxRadiusSheet
import com.lightxin.core.designsystem.theme.LxSurface
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextSecondary
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.core.designsystem.theme.LxTintStrong
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = LxSurface,
        scrimColor = Color.Black.copy(alpha = 0.38f),
        shape = RoundedCornerShape(topStart = LxRadiusSheet, topEnd = LxRadiusSheet),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LxTintStrong),
            )
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            content()
        }
    }
}

@Composable
fun LxConfirmSheet(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    confirmEnabled: Boolean = true,
) {
    LxBottomSheet(onDismiss = onDismiss) {
        Text(
            text = title,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
        )
        Text(
            text = message,
            fontSize = 12.5.sp,
            lineHeight = 21.sp,
            color = LxTextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        if (destructive) {
            LxDangerButton(text = confirmText, onClick = onConfirm, enabled = confirmEnabled)
        } else {
            LxPrimaryButton(text = confirmText, onClick = onConfirm, enabled = confirmEnabled)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(LxRadiusSm))
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "取消", fontSize = 13.sp, color = LxTextMuted)
        }
    }
}

@Composable
fun LxOptionSheet(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelect: (label: String, value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    LxBottomSheet(onDismiss = onDismiss) {
        Text(
            text = title,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        options.forEach { (label, value) ->
            val selected = value == selectedValue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LxRadiusSm))
                    .background(if (selected) LxTint else Color.Transparent)
                    .clickable(role = Role.RadioButton) {
                        onSelect(label, value)
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = LxText,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(LxAccent))
                }
            }
        }
    }
}

@Composable
fun LxDateTimeSheet(
    initial: LocalDateTime,
    title: String = "选择时间",
    onConfirm: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial.withSecond(0).withNano(0)) }
    var monthCursor by remember { mutableStateOf(YearMonth.from(initial)) }

    LxBottomSheet(onDismiss = onDismiss) {
        SheetTitle(title)
        MonthCalendar(
            monthCursor = monthCursor,
            onMonthChange = { monthCursor = it },
            isEndpoint = { it == value.toLocalDate() },
            isInRange = { false },
            onPick = { date -> value = LocalDateTime.of(date, value.toLocalTime()) },
        )
        TimeControls(time = value.toLocalTime(), onChange = { value = LocalDateTime.of(value.toLocalDate(), it) })
        SheetButtons(confirmEnabled = true, onDismiss = onDismiss, onConfirm = { onConfirm(value) })
    }
}

/**
 * 起止时间在同一个弹层里选：上方两个端点卡片切换正在编辑哪一端，日历高亮区间，
 * 常用时刻一键选中，± 微调分钟按 5 分钟一档。结束不晚于开始时禁止确定。
 */
@Composable
fun LxDateRangeSheet(
    initialStart: LocalDateTime?,
    initialEnd: LocalDateTime?,
    onConfirm: (LocalDateTime, LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
    title: String = "选择起止时间",
    editEndFirst: Boolean = false,
) {
    val seedStart = remember { (initialStart ?: lxNextSlot(LocalDateTime.now())).withSecond(0).withNano(0) }
    var start by remember { mutableStateOf(seedStart) }
    var end by remember {
        mutableStateOf(initialEnd?.withSecond(0)?.withNano(0)?.takeIf { it.isAfter(seedStart) } ?: seedStart.plusHours(2))
    }
    var editingStart by remember { mutableStateOf(!editEndFirst) }
    var monthCursor by remember { mutableStateOf(YearMonth.from(if (editEndFirst) end else start)) }
    val active = if (editingStart) start else end
    val valid = end.isAfter(start)

    fun update(next: LocalDateTime) {
        if (editingStart) {
            start = next
            if (!end.isAfter(next)) end = next.plusHours(2)
        } else {
            end = next
        }
    }

    LxBottomSheet(onDismiss = onDismiss) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SheetTitle(title, modifier = Modifier.weight(1f))
            if (valid) {
                Text(text = lxDurationLabel(start, end), fontSize = 13.sp, color = LxTextMuted)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EndpointCard(
                label = "开始",
                value = start,
                active = editingStart,
                onClick = {
                    editingStart = true
                    monthCursor = YearMonth.from(start)
                },
                modifier = Modifier.weight(1f),
            )
            EndpointCard(
                label = "结束",
                value = end,
                active = !editingStart,
                error = !valid,
                onClick = {
                    editingStart = false
                    monthCursor = YearMonth.from(end)
                },
                modifier = Modifier.weight(1f),
            )
        }
        MonthCalendar(
            monthCursor = monthCursor,
            onMonthChange = { monthCursor = it },
            isEndpoint = { it == start.toLocalDate() || it == end.toLocalDate() },
            isInRange = { it.isAfter(start.toLocalDate()) && it.isBefore(end.toLocalDate()) },
            onPick = { date -> update(LocalDateTime.of(date, active.toLocalTime())) },
        )
        TimeControls(time = active.toLocalTime(), onChange = { update(LocalDateTime.of(active.toLocalDate(), it)) })
        if (!valid) {
            Text(
                text = "结束时间需要晚于开始时间",
                fontSize = 12.5.sp,
                color = LxDanger,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        SheetButtons(confirmEnabled = valid, onDismiss = onDismiss, onConfirm = { onConfirm(start, end) })
    }
}

@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 18.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        color = LxText,
        modifier = modifier,
    )
}

@Composable
private fun EndpointCard(
    label: String,
    value: LocalDateTime,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (active) LxSurface else LxTint)
            .border(
                width = if (active) 1.5.dp else 0.5.dp,
                color = when {
                    error && !active -> LxDanger
                    active -> LxAccent
                    else -> LxHairline
                },
                shape = shape,
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = label, fontSize = 12.sp, color = if (active) LxAccentText else LxTextMuted)
        Text(
            text = lxShortDateTime(value),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (error) LxDanger else LxText,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthCalendar(
    monthCursor: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    isEndpoint: (LocalDate) -> Boolean,
    isInRange: (LocalDate) -> Boolean,
    onPick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SheetIconButton(
            icon = Icons.Outlined.KeyboardArrowLeft,
            description = "上个月",
            onClick = { onMonthChange(monthCursor.minusMonths(1)) },
        )
        Text(
            text = "${monthCursor.year} 年 ${monthCursor.monthValue} 月",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
        )
        SheetIconButton(
            icon = Icons.Outlined.KeyboardArrowRight,
            description = "下个月",
            onClick = { onMonthChange(monthCursor.plusMonths(1)) },
        )
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
            Text(
                text = day,
                fontSize = 11.5.sp,
                color = LxTextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }

    val leading = monthCursor.atDay(1).dayOfWeek.value - 1
    val daysInMonth = monthCursor.lengthOfMonth()
    val cells = ((leading + daysInMonth + 6) / 7) * 7
    Column(modifier = Modifier.fillMaxWidth()) {
        (0 until cells).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { index ->
                    val day = index - leading + 1
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day in 1..daysInMonth) {
                            val date = monthCursor.atDay(day)
                            val endpoint = isEndpoint(date)
                            val inRange = isInRange(date)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            endpoint -> LxAccent
                                            inRange -> LxTintStrong
                                            else -> Color.Transparent
                                        },
                                    )
                                    .then(
                                        if (date == today && !endpoint) Modifier.border(0.5.dp, LxTextFaint, CircleShape) else Modifier,
                                    )
                                    .clickable(role = Role.Button) { onPick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day.toString(),
                                    fontSize = 14.sp,
                                    color = if (endpoint) LxOnAccent else LxTextSecondary,
                                    fontWeight = if (endpoint) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val QuickTimes = listOf(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(12, 0), LocalTime.of(14, 0), LocalTime.of(18, 0))

/** 常用时刻 + 横向 ± 微调；按钮 40dp，分钟 5 分钟一档，避免点几十下。 */
@Composable
private fun TimeControls(time: LocalTime, onChange: (LocalTime) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickTimes.forEach { quick ->
            val selected = quick == time
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(LxRadiusSm))
                    .background(if (selected) LxText else LxTint)
                    .clickable(role = Role.Button) { onChange(quick) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = quick.format(TimeFormatter),
                    fontSize = 13.sp,
                    color = if (selected) LxCanvas else LxTextSecondary,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LxTint)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "时间",
            fontSize = 13.sp,
            color = LxTextMuted,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        StepButton(icon = Icons.Outlined.Remove, description = "减少一小时") { onChange(time.minusHours(1)) }
        Text(
            text = time.hour.toString().padStart(2, '0'),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        StepButton(icon = Icons.Outlined.Add, description = "增加一小时") { onChange(time.plusHours(1)) }
        Text(text = ":", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LxTextMuted, modifier = Modifier.padding(horizontal = 4.dp))
        StepButton(icon = Icons.Outlined.Remove, description = "减少五分钟") { onChange(lxStepMinutes(time, -5)) }
        Text(
            text = time.minute.toString().padStart(2, '0'),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        StepButton(icon = Icons.Outlined.Add, description = "增加五分钟") { onChange(lxStepMinutes(time, 5)) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = LxTextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SheetButtons(confirmEnabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LxGhostButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
        LxPrimaryButton(text = "确定", onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SheetIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = LxTextMuted, modifier = Modifier.size(20.dp))
    }
}

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WeekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 默认从下一个整点开始，而不是“现在 18:32”这种需要再调的时刻。 */
fun lxNextSlot(now: LocalDateTime): LocalDateTime =
    now.withMinute(0).withSecond(0).withNano(0).plusHours(1)

/** 分钟按 [delta] 步进并对齐到 5 分钟刻度，跨小时进位。 */
fun lxStepMinutes(time: LocalTime, delta: Int): LocalTime {
    val aligned = time.withMinute(time.minute / 5 * 5)
    val base = if (delta < 0 && aligned != time) aligned.plusMinutes(5) else aligned
    return base.plusMinutes(delta.toLong())
}

fun lxShortDateTime(value: LocalDateTime): String =
    "%d/%d %s %02d:%02d".format(
        value.monthValue,
        value.dayOfMonth,
        WeekLabels[value.dayOfWeek.value - 1],
        value.hour,
        value.minute,
    )

fun lxDurationLabel(start: LocalDateTime, end: LocalDateTime): String {
    val minutes = Duration.between(start, end).toMinutes()
    if (minutes <= 0) return ""
    val days = minutes / (60 * 24)
    val hours = (minutes % (60 * 24)) / 60
    val rest = minutes % 60
    return buildString {
        append("共")
        if (days > 0) append(" $days 天")
        if (hours > 0) append(" $hours 小时")
        if (rest > 0) append(" $rest 分钟")
    }
}

@Composable
fun LxTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LxInput(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        enabled = enabled,
        singleLine = false,
    )
}

private val LxDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatLxDateTime(value: LocalDateTime): String = value.format(LxDateTimeFormatter)

fun parseLxDateTime(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw, LxDateTimeFormatter) }.getOrNull()

fun shortLxDate(value: LocalDate): String =
    "${value.monthValue}/${value.dayOfMonth}"
