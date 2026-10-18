package com.lightxin.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun LxNavBar(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LxCanvas)
            .statusBarsPadding()
            .height(46.dp)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
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
            .padding(top = 4.dp),
    ) {
        Text(
            text = title,
            fontSize = 26.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            color = LxText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun LxSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
        color = LxTextFaint,
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

data class LxTabItem(val text: String, val count: Int? = null)

@Composable
fun LxFilterTabs(
    items: List<LxTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(LxRadiusSm)
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier.background(LxSurface).border(0.5.dp, LxHairline, shape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.text,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) LxText else LxTextMuted,
                )
                if (item.count != null) {
                    Text(
                        text = item.count.toString(),
                        fontSize = 11.sp,
                        color = if (selected) LxTextMuted else LxTextFaint,
                    )
                }
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
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = when {
                !enabled -> LxTextFaint
                selected -> LxCanvas
                else -> LxTextSecondary
            },
            maxLines = maxLines,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
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
            .background(LxTint)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(lxToneColor(tone)))
            Text(
                text = title,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = lxToneColor(tone),
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = LxTextMuted,
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

@Composable
fun LxMetaGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = label, fontSize = 11.sp, color = LxTextFaint)
                        Text(
                            text = value.ifBlank { "—" },
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LxText,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LxMetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: LxTone? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = LxTextMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (tone != null) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(lxToneColor(tone)))
            }
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
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
            color = if (enabled) LxText else LxTextFaint,
        )
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
    Column(modifier = modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 11.5.sp, color = LxTextMuted)
            if (required) {
                Text(text = " *", fontSize = 11.5.sp, color = LxAccent)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
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
            fontSize = 14.5.sp,
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
                    .then(if (singleLine) Modifier.height(48.dp) else Modifier.heightIn(min = 92.dp))
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
                            fontSize = 14.5.sp,
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
) {
    val shape = RoundedCornerShape(LxRadiusSm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (enabled) LxSurface else LxTint)
            .border(0.5.dp, LxHairline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.ifBlank { placeholder },
            fontSize = 14.5.sp,
            color = if (value.isBlank()) LxTextFaint else LxText,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = LxTextFaint,
        )
    }
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
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = LxTextMuted)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = value.ifBlank { placeholder },
                fontSize = 13.5.sp,
                fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
                color = if (value.isBlank()) LxTextFaint else LxText,
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LxTextFaint,
                    modifier = Modifier.size(16.dp),
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 19.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = LxText,
        )
        Text(
            text = body,
            fontSize = 12.5.sp,
            lineHeight = 22.sp,
            color = LxTextMuted,
            modifier = Modifier.padding(top = 10.dp),
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
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = LxTextMuted,
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
