package com.lightxin.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxTerraSoft
import com.lightxin.core.designsystem.theme.animatedPressColor

@Composable
fun LxIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedContainerColor: Color = LxTerraSoft,
    containerColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background by animatedPressColor(
        interactionSource = interactionSource,
        rest = containerColor,
        pressed = if (enabled) pressedContainerColor else containerColor,
    )
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) LxMotion.PressedScaleButton else 1f,
        animationSpec = LxMotion.PressScale,
        label = "lxIconScale",
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
