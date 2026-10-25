package com.lightxin.core.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

@Composable
fun Modifier.lxPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 1f,
    shape: Shape = RectangleShape,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = pressed && enabled
    val scale by animateFloatAsState(
        targetValue = if (active) pressedScale else 1f,
        animationSpec = if (active) LxMotion.PressIn else LxMotion.PressRelease,
        label = "lxPressableScale",
    )
    val overlay by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            if (active) LxMotion.PressInMs else LxMotion.PressOutMs,
            easing = LxMotion.EaseInOut,
        ),
        label = "lxPressableOverlay",
    )
    val tint = LxText
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (overlay > 0f) drawRect(tint.copy(alpha = LxMotion.PressOverlayAlpha * overlay))
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
