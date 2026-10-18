package com.lightxin.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.theme.LxCard
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.animatedPressColor
import com.lightxin.core.designsystem.theme.animatedPressScale

@Composable
fun LxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium // 16dp

    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val bg by animatedPressColor(interactionSource, rest = LxCard, pressed = LxCream)
        val scale by animatedPressScale(
            interactionSource = interactionSource,
            pressedScale = LxMotion.PressedScaleCard,
        )

        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = bg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = LxCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = content,
        )
    }
}
