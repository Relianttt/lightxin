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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun RunningSimScreen(
    onBack: () -> Unit,
    onNavigateResult: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.shouldNavigateToResult) {
        if (uiState.shouldNavigateToResult) {
            viewModel.consumeResultNavigation()
            onNavigateResult()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "模拟提交", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "生成一条合理的校园轨迹后直接上传。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                LxCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "跑步参数",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LxTextField(
                            value = uiState.simDistance,
                            onValueChange = viewModel::updateSimDistance,
                            label = "距离（km）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LxTextField(
                            value = uiState.simDurationMinutes,
                            onValueChange = viewModel::updateSimDurationMinutes,
                            label = "时长（分钟）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }

            item {
                LxCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "开始时间",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatTime(uiState.simStartTimeMillis),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(20L, 30L, 45L).forEach { minutes ->
                                val targetTime = System.currentTimeMillis() - minutes * 60_000L
                                FilterChip(
                                    selected = uiState.simStartTimeMillis == targetTime,
                                    onClick = { viewModel.updateSimStartTime(targetTime) },
                                    label = { Text("前推 ${minutes} 分钟") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            item {
                val speed = viewModel.simulationSpeedKmh()
                LxCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "校验结果",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (speed == null) {
                                "请先输入距离与时长"
                            } else {
                                String.format(Locale.US, "预估平均速度 %.1f km/h，建议保持在 6-15 km/h。", speed)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!uiState.startError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = uiState.startError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                LxButton(
                    text = if (uiState.isSubmittingSimulation) "正在提交..." else "生成轨迹并上传",
                    onClick = { scope.launch { viewModel.submitSimulation() } },
                    enabled = !uiState.isSubmittingSimulation,
                )
            }
        }
    }
}

private fun formatTime(timeMillis: Long): String {
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA))
}
