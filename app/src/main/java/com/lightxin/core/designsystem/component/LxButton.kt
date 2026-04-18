package com.lightxin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.LxSandDeep
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxTerraSoft

/** 主按钮：terra 填充 + 白字 + 52dp 高 + RLg(16dp) 圆角 */
@Composable
fun LxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LxButtonFrame(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = LxTerra,
        pressedContainerColor = LxTerra.copy(alpha = 0.88f),
        contentColor = Color.White,
        disabledContainerColor = LxTerra.copy(alpha = 0.4f),
        disabledContentColor = Color.White.copy(alpha = 0.82f),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** 次按钮：sand 填充 + ink-muted 字（原型欢迎页「暂不进入」样式） */
@Composable
fun LxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LxButtonFrame(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = LxSand,
        pressedContainerColor = LxCream,
        contentColor = LxInkMuted,
        disabledContainerColor = LxSand.copy(alpha = 0.65f),
        disabledContentColor = LxInkMuted.copy(alpha = 0.6f),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = LxInkMuted)
    }
}

@Composable
fun LxOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LxButtonFrame(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = Color.Transparent,
        pressedContainerColor = LxCream,
        contentColor = LxInk,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = LxInkMuted.copy(alpha = 0.6f),
        borderColor = LxSandDeep,
        disabledBorderColor = LxSandDeep.copy(alpha = 0.5f),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = LxInk)
    }
}

@Composable
fun LxTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (isPressed) LxTerraSoft else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier,
            style = MaterialTheme.typography.labelLarge,
            color = LxTerra,
        )
    }
}

@Composable
private fun LxButtonFrame(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    containerColor: Color,
    pressedContainerColor: Color,
    contentColor: Color,
    disabledContainerColor: Color,
    disabledContentColor: Color,
    borderColor: Color = Color.Transparent,
    disabledBorderColor: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = MaterialTheme.shapes.medium
    val background = when {
        !enabled -> disabledContainerColor
        isPressed -> pressedContainerColor
        else -> containerColor
    }
    val resolvedBorder = if (enabled) borderColor else disabledBorderColor
    val resolvedContent = if (enabled) contentColor else disabledContentColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(background)
            .then(
                if (resolvedBorder != Color.Transparent) {
                    Modifier.border(width = 1.dp, color = resolvedBorder, shape = shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides resolvedContent,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                content()
            }
        }
    }
}
