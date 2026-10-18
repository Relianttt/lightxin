package com.lightxin.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxAccentText
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxDangerFill
import com.lightxin.core.designsystem.theme.LxHairline
import com.lightxin.core.designsystem.theme.LxOnAccent
import com.lightxin.core.designsystem.theme.LxRadiusLg
import com.lightxin.core.designsystem.theme.LxRadiusMd
import com.lightxin.core.designsystem.theme.LxRadiusSm
import com.lightxin.core.designsystem.theme.LxStateSuccess
import com.lightxin.core.designsystem.theme.LxSurface
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextFaint
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTextSecondary
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.core.designsystem.theme.LxTintStrong
import com.lightxin.core.designsystem.theme.LxStateWarning

enum class LxTone { Neutral, Accent, Success, Warning, Danger }

@Composable
fun lxToneColor(tone: LxTone): Color = when (tone) {
    LxTone.Neutral -> LxTextMuted
    LxTone.Accent -> LxAccent
    LxTone.Success -> LxStateSuccess
    LxTone.Warning -> LxStateWarning
    LxTone.Danger -> LxDanger
}

/**
 * 顶栏：返回 + 右侧动作。页面大标题滚出视口后（[collapsed]），在返回键旁淡入小标题并显示底部细线，
 * 避免滚动后只剩一个箭头、不知道身在哪页。
 */
@Composable
fun LxNavBar(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsed: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleAlpha by animateFloatAsState(
        targetValue = if (collapsed && !title.isNullOrBlank()) 1f else 0f,
        animationSpec = tween(160),
        label = "lxNavTitle",
    )
    val dividerAlpha by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(160),
        label = "lxNavDivider",
    )
    Column(modifier = modifier.fillMaxWidth().background(LxCanvas)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = LxTextSecondary,
                    )
                }
            } else {
                Spacer(Modifier.width(18.dp))
            }
            Text(
                text = title.orEmpty(),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp)
                    .alpha(titleAlpha),
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .alpha(dividerAlpha)
                .background(LxHairline),
        )
    }
}

/** 第一个列表项（页面大标题）滚过一小段后即视为折叠。 */
@Composable
fun LazyListState.lxHeaderCollapsed(): Boolean {
    val threshold = with(LocalDensity.current) { 36.dp.roundToPx() }
    val collapsed by remember(this, threshold) {
        derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > threshold }
    }
    return collapsed
}

@Composable
fun LxTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LxAccentText,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LxRadiusSm))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) color else LxTextFaint,
        )
    }
}

@Composable
fun LxPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = LxText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun LxSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.5.sp,
        color = LxTextMuted,
        modifier = modifier,
    )
}

@Composable
fun LxStatus(text: String, tone: LxTone, modifier: Modifier = Modifier) {
    val color = lxToneColor(tone)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(
            text = text.ifBlank { "处理中" },
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * 状态 + 元信息合成一行：状态放最前，保证不会被省略号吃掉；标题因此能占满整行宽度。
 * 需要处理的状态（Accent/Warning/Danger）文字着色；已结束的状态只保留彩色圆点，文字退回灰色。
 */
@Composable
fun LxStatusLine(
    status: String,
    tone: LxTone,
    meta: String,
    modifier: Modifier = Modifier,
) {
    val dotColor = lxToneColor(tone)
    val emphasize = tone == LxTone.Accent || tone == LxTone.Warning || tone == LxTone.Danger
    val statusColor = if (emphasize) dotColor else LxTextMuted
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = statusColor, fontWeight = if (emphasize) FontWeight.Medium else FontWeight.Normal)) {
                    append(status.ifBlank { "处理中" })
                }
                if (meta.isNotBlank()) {
                    withStyle(SpanStyle(color = LxTextMuted)) { append("  ·  $meta") }
                }
            },
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

data class LxTabItem(val text: String, val count: Int? = null)

/** 分段控件：同一条底轨里切换，选中项是白块，避免“一个像按钮、一个像文字”。 */
@Composable
fun LxFilterTabs(
    items: List<LxTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LxTintStrong)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(LxRadiusSm)
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .then(if (selected) Modifier.shadow(1.dp, shape) else Modifier)
                    .clip(shape)
                    .background(if (selected) LxSurface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.text,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) LxText else LxTextMuted,
                )
                if (item.count != null) {
                    Text(
                        text = item.count.toString(),
                        fontSize = 12.sp,
                        color = if (selected) LxTextMuted else LxTextFaint,
                    )
                }
            }
        }
    }
}

/**
 * 单选组：两三个短选项时等宽平铺；选项多或文字长时按内容宽度换行，
 * 避免固定三列把“IT（智能电子设备）”折成两行、或只有两项时空出一格。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LxChoiceGroup(
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val equalWidth = options.size <= 3 && options.all { it.first.length <= 4 }
    if (equalWidth) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) ->
                LxPill(
                    text = label,
                    selected = selectedValue == value,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    height = 40.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, value) ->
                LxPill(
                    text = label,
                    selected = selectedValue == value,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    height = 40.dp,
                    modifier = Modifier.widthIn(min = 64.dp),
                    horizontalPadding = 14.dp,
                )
            }
        }
    }
}

@Composable
fun LxPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 38.dp,
    maxLines: Int = 1,
    horizontalPadding: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(LxRadiusSm)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(
                when {
                    !enabled -> LxTint.copy(alpha = 0.5f)
                    selected -> LxText
                    else -> LxTint
                },
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = when {
                !enabled -> LxTextFaint
                selected -> LxCanvas
                else -> LxTextSecondary
            },
            maxLines = maxLines,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
    }
}

@Composable
fun LxPanel(
    modifier: Modifier = Modifier,
    radius: Dp = LxRadiusLg,
    padding: Dp = 16.dp,
    shadow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .then(if (shadow) Modifier.shadow(10.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(LxSurface)
            .border(0.5.dp, LxHairline, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun LxCallout(
    tone: LxTone,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LxRadiusMd))
            .background(LxTintStrong)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(lxToneColor(tone)))
            Text(
                text = title,
                fontSize = 15.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = lxToneColor(tone),
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = LxTextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        content()
    }
}

enum class LxStepState { Done, Current, Todo }

data class LxStepItem(val label: String, val state: LxStepState)

@Composable
fun LxStepBar(steps: List<LxStepItem>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when (step.state) {
                        LxStepState.Done -> LxTextMuted
                        LxStepState.Current -> LxAccent
                        LxStepState.Todo -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .then(
                                if (step.state == LxStepState.Todo) {
                                    Modifier.border(1.5.dp, LxTextFaint, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (index != steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.5.dp)
                                .background(if (step.state == LxStepState.Done) LxTextMuted else LxHairline),
                        )
                    }
                }
                Text(
                    text = step.label,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (step.state == LxStepState.Current) FontWeight.Medium else FontWeight.Normal,
                    color = if (step.state == LxStepState.Current) LxTextSecondary else LxTextFaint,
                    modifier = Modifier.padding(top = 6.dp, end = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun LxFlowStep(
    title: String,
    time: String? = null,
    state: LxStepState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val dotColor = when (state) {
            LxStepState.Done -> LxTextMuted
            LxStepState.Current -> LxAccent
            LxStepState.Todo -> Color.Transparent
        }
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
                .then(
                    if (state == LxStepState.Todo) {
                        Modifier.border(1.dp, LxTextFaint, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
        Row(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                fontWeight = if (state == LxStepState.Current) FontWeight.SemiBold else FontWeight.Medium,
                color = if (state == LxStepState.Todo) LxTextMuted else LxText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!time.isNullOrBlank()) {
                Text(
                    text = time,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(start = 7.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 键值网格：短值两两并排，长值（编号、地址、学院全称）独占一行，避免两列互相挤压。
 * 值用常规字重——一页八个粗值等于没有重点，重点交给状态块和标题。
 */
@Composable
fun LxMetaGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val rows = remember(items) { lxMetaRows(items) }
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = label, fontSize = 12.sp, lineHeight = 17.sp, color = LxTextFaint)
                        Text(
                            text = value.ifBlank { "—" },
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = LxText,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (row.size == 1 && !lxMetaIsWide(row.first().second)) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 半宽列在 393dp 屏上大约放得下 10 个汉字宽度；超过就独占一行。 */
internal fun lxMetaIsWide(value: String): Boolean =
    value.sumOf { if (it.code > 0x2E80) 1.0 else 0.58 } > 10.0

internal fun lxMetaRows(items: List<Pair<String, String>>): List<List<Pair<String, String>>> {
    val rows = mutableListOf<List<Pair<String, String>>>()
    var pending: Pair<String, String>? = null
    items.forEach { item ->
        if (lxMetaIsWide(item.second)) {
            rows += listOf(item)
        } else {
            val held = pending
            if (held == null) {
                pending = item
            } else {
                rows += listOf(held, item)
                pending = null
            }
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}

@Composable
fun LxMetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: LxTone? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.5.sp, color = LxTextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (tone != null) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(lxToneColor(tone)))
            }
            Text(
                text = value,
                fontSize = 14.sp,
                color = LxText,
            )
        }
    }
}

@Composable
fun LxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(LxRadiusSm))
            .background(if (enabled) LxAccent else LxAccent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) LxOnAccent else LxOnAccent.copy(alpha = 0.75f),
        )
    }
}

@Composable
fun LxGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(LxRadiusSm))
            .background(LxSurface)
            .border(0.5.dp, LxHairline, RoundedCornerShape(LxRadiusSm))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled -> LxTextFaint
                contentColor != Color.Unspecified -> contentColor
                else -> LxText
            },
        )
    }
}

/**
 * 吸底操作栏：表单的提交按钮固定在拇指区，不必滚到最底才看见。
 * 上沿一条细线把它和滚动内容分开。
 */
@Composable
fun LxBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LxCanvas)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(LxHairline))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            content = content,
        )
    }
}

/** 列表页的主动作：右下角悬浮，替代右上角难够到的小字。 */
@Composable
fun LxFloatingAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = LxAccent, spotColor = LxAccent)
            .clip(shape)
            .background(LxAccent)
            .clickable(role = Role.Button, onClick = onClick)
            .height(52.dp)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = LxOnAccent, modifier = Modifier.size(20.dp))
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LxOnAccent)
    }
}

@Composable
fun LxDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(LxRadiusSm))
            .background(if (enabled) LxDangerFill else LxDangerFill.copy(alpha = 0.45f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
fun LxField(
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 13.sp, lineHeight = 18.sp, color = LxTextSecondary)
            if (required) {
                Text(text = " *", fontSize = 13.sp, color = LxAccent)
            }
        }
        Spacer(modifier = Modifier.height(9.dp))
        content()
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = LxDanger,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun LxInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(LxRadiusSm)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = if (enabled) LxText else LxTextFaint,
        ),
        cursorBrush = SolidColor(LxAccent),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.height(50.dp) else Modifier.heightIn(min = 96.dp))
                    .clip(shape)
                    .background(if (enabled) LxSurface else LxTint)
                    .border(
                        width = if (focused) 1.dp else 0.5.dp,
                        color = when {
                            !enabled -> LxHairline
                            focused -> LxAccent
                            else -> LxHairline
                        },
                        shape = shape,
                    )
                    .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 13.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = LxTextFaint,
                            maxLines = if (singleLine) 1 else 3,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
fun LxSelectRow(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector = Icons.Outlined.KeyboardArrowDown,
) {
    val shape = RoundedCornerShape(LxRadiusSm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(if (enabled) LxSurface else LxTint)
            .border(0.5.dp, LxHairline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.ifBlank { placeholder },
            fontSize = 15.sp,
            color = if (value.isBlank()) LxTextFaint else LxText,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = LxTextFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun LxGroupDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 15.dp)
            .height(0.5.dp)
            .background(LxHairline),
    )
}

@Composable
fun LxGroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LxSurface)
            .border(0.5.dp, LxHairline, shape),
        content = content,
    )
}

@Composable
fun LxGroupRow(
    label: String,
    value: String,
    placeholder: String = "请选择",
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 14.sp, color = LxTextMuted)
        Row(
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = value.ifBlank { placeholder },
                fontSize = 15.sp,
                color = if (value.isBlank() || !enabled) LxTextFaint else LxText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LxTextFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun LxEmptyState(
    title: String,
    body: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            fontSize = 13.5.sp,
            lineHeight = 22.sp,
            color = LxTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (actionText != null && onAction != null) {
            LxTextAction(
                text = actionText,
                onClick = onAction,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
fun LxErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            fontSize = 13.5.sp,
            lineHeight = 22.sp,
            color = LxTextMuted,
            textAlign = TextAlign.Center,
        )
        LxSecondaryButton(
            text = "重试",
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(0.5f),
        )
    }
}

@Composable
fun LxSkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "lxSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "lxSkeletonAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(LxTintStrong.copy(alpha = alpha)),
    )
}

@Composable
fun LxSkeletonList(
    count: Int = 5,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        LxSkeletonBlock(height = 10.dp, modifier = Modifier.padding(top = 22.dp, bottom = 14.dp), widthFraction = 0.24f)
        repeat(count) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
                LxSkeletonBlock(height = 14.dp, widthFraction = 0.62f)
                Spacer(Modifier.height(9.dp))
                LxSkeletonBlock(height = 10.dp, widthFraction = 0.82f)
                Spacer(Modifier.height(7.dp))
                LxSkeletonBlock(height = 10.dp, widthFraction = 0.46f)
            }
        }
    }
}
