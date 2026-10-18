package com.lightxin.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.theme.LxAccent
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
    var selectedDate by remember { mutableStateOf(initial.toLocalDate()) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var monthCursor by remember { mutableStateOf(YearMonth.from(initial)) }

    LxBottomSheet(onDismiss = onDismiss) {
        Text(
            text = title,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SheetIconButton(
                icon = Icons.Outlined.KeyboardArrowLeft,
                description = "上个月",
                onClick = { monthCursor = monthCursor.minusMonths(1) },
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
                onClick = { monthCursor = monthCursor.plusMonths(1) },
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Text(
                    text = day,
                    fontSize = 10.5.sp,
                    color = LxTextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }

        val firstDay = monthCursor.atDay(1)
        val leading = firstDay.dayOfWeek.value - 1
        val daysInMonth = monthCursor.lengthOfMonth()
        val cells = ((leading + daysInMonth + 6) / 7) * 7
        Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            (0 until cells).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { index ->
                        val day = index - leading + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day in 1..daysInMonth) {
                                val date = monthCursor.atDay(day)
                                val selected = date == selectedDate
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .then(if (selected) Modifier.background(LxAccent) else Modifier)
                                        .clickable(role = Role.Button) { selectedDate = date },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.toString(),
                                        fontSize = 13.5.sp,
                                        color = if (selected) LxOnAccent else LxTextSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LxTint)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "时间", fontSize = 12.sp, color = LxTextMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeStepper(
                    value = hour,
                    onChange = { hour = it },
                    max = 23,
                    label = "时",
                )
                Text(
                    text = ":",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxTextMuted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                TimeStepper(
                    value = minute,
                    onChange = { minute = it },
                    max = 59,
                    label = "分",
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LxGhostButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            LxPrimaryButton(
                text = "确定",
                onClick = { onConfirm(LocalDateTime.of(selectedDate, LocalTime.of(hour, minute))) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SheetIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = LxTextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TimeStepper(
    value: Int,
    onChange: (Int) -> Unit,
    max: Int,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 20.dp)
                .clickable(role = Role.Button) { onChange(if (value >= max) 0 else value + 1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "增加$label", tint = LxTextMuted, modifier = Modifier.size(16.dp))
        }
        Text(
            text = value.toString().padStart(2, '0'),
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
        )
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 20.dp)
                .clickable(role = Role.Button) { onChange(if (value <= 0) max else value - 1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "减少$label", tint = LxTextMuted, modifier = Modifier.size(16.dp))
        }
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
