package com.lightxin.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxSandDeep
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxTerraSoft

@Composable
fun LxChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    val targetBorderColor = when {
        !enabled -> LxSandDeep.copy(alpha = 0.5f)
        selected -> LxTerra
        else -> LxSandDeep
    }
    val targetContainerColor = when {
        !enabled -> LxCream.copy(alpha = 0.65f)
        selected -> if (isPressed) LxTerraSoft.copy(alpha = 0.85f) else LxTerraSoft
        isPressed -> LxCream.copy(alpha = 0.8f)
        else -> LxCream
    }
    val targetTextColor = when {
        !enabled -> LxInkMuted.copy(alpha = 0.6f)
        selected -> LxTerra
        else -> LxInkMuted
    }
    val borderColor by animateColorAsState(targetBorderColor, LxMotion.ColorSpring, label = "lxChipBorder")
    val containerColor by animateColorAsState(targetContainerColor, LxMotion.ColorSpring, label = "lxChipBg")
    val textColor by animateColorAsState(targetTextColor, LxMotion.ColorSpring, label = "lxChipText")
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) LxMotion.PressedScaleButton else 1f,
        animationSpec = LxMotion.PressScale,
        label = "lxChipScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}
