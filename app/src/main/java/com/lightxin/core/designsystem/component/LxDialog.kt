package com.lightxin.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LxCard
import com.lightxin.core.designsystem.theme.LxCardBorder
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxTerra
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LxDialogConfirmTone {
    Primary,
    Destructive,
}

@Composable
fun LxDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmTone: LxDialogConfirmTone = LxDialogConfirmTone.Primary,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // 出场：卡片 0.98→1 + 淡入；关闭时先反向播完再回调，避免“啪”地消失。
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scope = rememberCoroutineScope()
    val cardScale by animateFloatAsState(
        targetValue = if (shown) 1f else LxMotion.PressedScaleButton,
        animationSpec = LxMotion.Soft,
        label = "lxDialogScale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = LxMotion.StateMs, easing = LxMotion.EaseOut),
        label = "lxDialogAlpha",
    )
    fun animateOutThen(action: () -> Unit) {
        shown = false
        scope.launch {
            delay(LxMotion.PressOutMs.toLong())
            action()
        }
    }

    Dialog(
        onDismissRequest = { animateOutThen(onDismissRequest) },
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = dismissOnBackPress,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                .alpha(cardAlpha)
                .padding(horizontal = 24.dp),
        ) {
            LxCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = LxInk,
                    )
                    if (!message.isNullOrBlank()) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LxInkMuted,
                        )
                    }
                    content?.invoke(this)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!dismissText.isNullOrBlank()) {
                            DialogAction(
                                text = dismissText,
                                color = LxInkMuted,
                                onClick = { animateOutThen(onDismiss ?: onDismissRequest) },
                            )
                        }
                        DialogAction(
                            text = confirmText,
                            color = if (confirmTone == LxDialogConfirmTone.Destructive) LxRose else LxTerra,
                            onClick = { animateOutThen(onConfirm) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(indication = null, interactionSource = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
