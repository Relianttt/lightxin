package com.lightxin.feature.running.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDetailRow
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.running.domain.RunningResult

@Composable
fun RunningResultScreen(
    onBack: () -> Unit,
    onBackToHome: () -> Unit,
    onBackToRunning: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val result = uiState.lastResult

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "跑步结果", onBack = onBack) },
    ) { padding ->
        if (result == null) {
            LxEmpty(
                message = "当前没有可展示的结果",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ResultStatusCard(result) }
            item { ResultMetricsCard(result) }
            item {
                LxButton(
                    text = "返回跑步首页",
                    onClick = {
                        viewModel.clearResult()
                        onBackToRunning()
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                LxOutlinedButton(
                    text = "返回首页",
                    onClick = {
                        viewModel.clearResult()
                        onBackToHome()
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultStatusCard(result: RunningResult) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (result.success) "上传完成" else "上传失败",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (result.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (result.uploadId.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "记录 ID: ${result.uploadId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultMetricsCard(result: RunningResult) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "本次摘要",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            LxDetailRow(label = "开始时间", value = result.startDate, showDivider = false)
            LxDetailRow(label = "结束时间", value = result.endDate, showDivider = false)
            LxDetailRow(label = "距离", value = String.format("%.2f km", result.distanceKm), showDivider = false)
            LxDetailRow(label = "时长", value = "${result.durationSeconds} 秒", showDivider = false)
            LxDetailRow(label = "速度", value = String.format("%.2f km/h", result.speedKmh), showDivider = false)
            LxDetailRow(label = "轨迹点", value = "${result.pointCount} 个", showDivider = false)
        }
    }
}
