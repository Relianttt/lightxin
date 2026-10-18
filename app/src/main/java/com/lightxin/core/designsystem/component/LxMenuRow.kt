package com.lightxin.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkGhost
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxMotion
import com.lightxin.core.designsystem.theme.LxSand
import com.lightxin.core.designsystem.theme.animatedPressColor

/**
 * 设置/更多页统一菜单行。左侧图标、标题、可选说明与尾值、右箭头。
 * 手感（按压配色 + 临界阻尼）集中在这里，各页面只描述内容。
 */
@Composable
fun LxMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hint: String? = null,
    hintColor: Color = LxInkGhost,
    hintHighlight: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animatedPressColor(interactionSource, Color.Transparent, LxCream)
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) LxMotion.PressedScaleCard else 1f,
        animationSpec = LxMotion.PressScale,
        label = "lxMenuScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LxInkSoft,
            modifier = Modifier.size(22.dp),
        )
        if (subtitle == null) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = LxInk,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, color = LxInk)
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = LxInkSoft,
                )
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 13.sp,
                color = if (hintHighlight) hintColor else hintColor,
                fontWeight = if (hintHighlight) FontWeight.Medium else FontWeight.Normal,
            )
        }
        Text(
            text = "›",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            color = LxInkGhost,
        )
    }
}

@Composable
fun LxMenuDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 54.dp, end = 18.dp)
            .height(1.dp)
            .background(LxSand),
    )
}
