package com.lightxin.feature.running.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import com.lightxin.core.designsystem.component.LxChoiceChip
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
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

            // 路线来源卡
            item {
                LxCard {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            text = "路线来源",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val template = uiState.defaultTemplate
                        if (template != null) {
                            Text(
                                text = "默认模板 · ${template.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(
                                    "%.2f km · %d 个点",
                                    template.totalDistanceMeters / 1000.0,
                                    template.pointCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "使用内置校园路线",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.templateCount > 0)
                                    "未设置默认模板，前往「我的 / 路线模拟」选一条"
                                else
                                    "尚无模板，前往「我的 / 路线模拟」录制一条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // 卡片1: 跑步参数 + 建议时间
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
                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(20, 30, 45).forEach { minutes ->
                                LxChoiceChip(
                                    text = "$minutes 分钟",
                                    selected = uiState.selectedSimDurationPresetMinutes == minutes,
                                    onClick = { viewModel.selectSimDurationPreset(minutes) },
                                )
                            }
                        }
                    }
                }
            }

            // 卡片2: 开始时间 + 校验
            item {
                LxCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "开始时间",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTime(uiState.simStartTimeMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        val speed = viewModel.simulationSpeedKmh()
                        Text(
                            text = "校验",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (speed == null) {
                                "请输入距离与时长"
                            } else {
                                val inRange = speed in 6.0..15.0
                                String.format(
                                    Locale.US,
                                    "预估速度 %.1f km/h %s",
                                    speed,
                                    if (inRange) "✓" else "",
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (speed != null && speed in 6.0..15.0) {
                                LxInk
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )

                        if (!uiState.simError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.simError!!,
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
