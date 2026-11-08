package com.lightxin.core.designsystem.component

import androidx.compose.animation.core.Animatable
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
    /** 底部操作行左侧的次要操作（如"跳过此版本"），与右侧的稍后/确认同一行、同一套 [DialogAction]。 */
    leadingText: String? = null,
    onLeading: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // 关闭时先播完退场再回调，进场与退场使用独立缩放幅度。
    // 用 Animatable 而不是 delay()：系统关闭动画时 animateTo 立即返回，不会白等固定时长。
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(LxMotion.OverlayEnterMs, easing = LxMotion.EaseEnter))
    }
    val scope = rememberCoroutineScope()
    fun animateOutThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(LxMotion.OverlayExitMs, easing = LxMotion.EaseExit))
            action()
        }
    }
    val baseScale = if (closing) LxMotion.OverlayExitScale else LxMotion.OverlayInitialScale
    val cardScale = baseScale + (1f - baseScale) * progress.value

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
                .alpha(progress.value)
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
                        // 有左侧次要操作时贴两端，否则维持原来的全部右对齐
                        horizontalArrangement = if (leadingText.isNullOrBlank()) Arrangement.End else Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!leadingText.isNullOrBlank()) {
                            DialogAction(
                                text = leadingText,
                                color = LxInkMuted,
                                onClick = { animateOutThen(onLeading ?: onDismissRequest) },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
