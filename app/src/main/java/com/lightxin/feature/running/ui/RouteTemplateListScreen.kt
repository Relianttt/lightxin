package com.lightxin.feature.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxTerraSoft
import com.lightxin.core.designsystem.theme.LxWarning
import com.lightxin.feature.running.domain.RouteQualityStatus
import com.lightxin.feature.running.domain.RouteTemplate

@Composable
fun RouteTemplateListScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "模板管理", onBack = onBack) },
    ) { padding ->
        if (uiState.templates.isEmpty()) {
            LxEmpty(
                message = "还没有任何模板，去录制一条吧",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.templates, key = { it.id }) { template ->
                    TemplateCard(
                        template = template,
                        onClick = { onOpenDetail(template.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: RouteTemplate,
    onClick: () -> Unit,
) {
    LxCard(onClick = onClick) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = template.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                    modifier = Modifier.weight(1f),
                )
                if (template.qualityStatus == RouteQualityStatus.WARNING) {
                    Badge(text = "警告", color = LxWarning)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (template.isDefault) {
                    Badge(text = "默认", color = LxTerra, bg = LxTerraSoft)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format(
                    "%.2f km · %d 个点 · %s",
                    template.totalDistanceMeters / 1000.0,
                    template.pointCount,
                    formatDate(template.createdAtMillis),
                ),
                fontSize = 13.sp,
                color = LxInkMuted,
            )
            template.lastUsedAtMillis?.let { used ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "最近使用 ${formatDate(used)}",
                    fontSize = 12.sp,
                    color = LxSage,
                )
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color = color.copy(alpha = 0.12f),
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
