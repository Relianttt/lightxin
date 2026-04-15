package com.lightxin.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxEmpty

@Composable
fun HomeDashboard(modifier: Modifier = Modifier, navController: NavHostController) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "轻小信",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "今天也要加油哦",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 今日课程
        QuickCard(
            icon = Icons.Default.CalendarMonth,
            title = "今日课程",
            subtitle = "暂无数据",
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 查寝签到
        QuickCard(
            icon = Icons.Default.CheckCircle,
            title = "查寝签到",
            subtitle = "暂无任务",
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 运动进度
        QuickCard(
            icon = Icons.Default.DirectionsRun,
            title = "运动进度",
            subtitle = "暂无数据",
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 劳动教育
        QuickCard(
            icon = Icons.Default.WorkHistory,
            title = "劳动教育",
            subtitle = "暂无数据",
        )
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    LxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun RunningPlaceholder(modifier: Modifier = Modifier) {
    LxEmpty(message = "跑步运动 - Phase 6 实现", modifier = modifier)
}

@Composable
fun ProfilePlaceholder(modifier: Modifier = Modifier) {
    LxEmpty(message = "我的 - Phase 7 实现", modifier = modifier)
}
