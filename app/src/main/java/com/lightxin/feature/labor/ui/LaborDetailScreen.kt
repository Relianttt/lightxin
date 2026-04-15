package com.lightxin.feature.labor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.labor.domain.ActivityDetail

@Composable
fun LaborDetailScreen(
    id: String,
    type: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LaborDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "活动详情", onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.error != null -> LxError(
                message = uiState.error!!,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            uiState.detail != null -> DetailContent(
                detail = uiState.detail!!,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: ActivityDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // 活动名称
        Text(
            text = detail.activityName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 详情卡片
        LxCard {
            Column(modifier = Modifier.padding(20.dp)) {
                DetailRow(label = "活动类型", value = detail.activityType)
                DetailRow(label = "活动级别", value = detail.activityLevel)
                DetailRow(label = "主办方", value = detail.organizer)
                DetailRow(label = "志愿时长", value = "%.1f".format(detail.serviceTimes))
                DetailRow(label = "日期", value = detail.createDate, showDivider = false)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
) {
    if (value.isBlank()) return

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}
