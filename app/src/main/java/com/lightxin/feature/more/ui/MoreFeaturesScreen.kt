package com.lightxin.feature.more.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Grading
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkGhost
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxSand

@Composable
fun MoreFeaturesScreen(
    onBack: () -> Unit,
    onNavigateLabor: () -> Unit,
    onNavigateExam: () -> Unit,
    onNavigateCredit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "更多功能", onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 16.dp)) {
            LxCard {
                Column {
                    MoreMenuRow(
                        icon = Icons.Outlined.WorkHistory,
                        title = "劳动教育",
                        onClick = onNavigateLabor,
                    )
                    MoreMenuDivider()
                    MoreMenuRow(
                        icon = Icons.Outlined.Grading,
                        title = "考试成绩",
                        onClick = onNavigateExam,
                    )
                    MoreMenuDivider()
                    MoreMenuRow(
                        icon = Icons.Outlined.EmojiEvents,
                        title = "素质学分",
                        onClick = onNavigateCredit,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg = if (isPressed) LxCream else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
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
        Text(
            text = title,
            fontSize = 15.sp,
            color = LxInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            color = LxInkGhost,
        )
    }
}

@Composable
private fun MoreMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp, end = 18.dp)
            .height(1.dp)
            .background(LxSand),
    )
}
