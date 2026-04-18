package com.lightxin.feature.running.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.running.domain.RunningTrackerState
import com.lightxin.feature.running.service.RunTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun RunningActiveScreen(
    onBack: () -> Unit,
    onNavigateResult: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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
        topBar = { LxTopBar(title = "跑步中", onBack = onBack) },
    ) { padding ->
        when {
            uiState.isUploadingRun -> LxLoading(modifier = Modifier.padding(padding))
            !uiState.trackerState.isSessionActive -> LxEmpty(
                message = "当前没有进行中的跑步会话",
                modifier = Modifier.padding(padding),
            )
            else -> RunningActiveContent(
                trackerState = uiState.trackerState,
                modifier = Modifier.padding(padding),
                onFinish = {
                    scope.launch {
                        RunTrackingService.stop(context)
                        viewModel.finishRealRun()
                    }
                },
                onCancel = {
                    RunTrackingService.cancel(context)
                    viewModel.abandonRealRun()
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun RunningActiveContent(
    trackerState: RunningTrackerState,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trackerState.startTimeMillis, trackerState.isSessionActive) {
        while (trackerState.isSessionActive) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val durationSeconds = ((nowMillis - trackerState.startTimeMillis) / 1000L).coerceAtLeast(0L)
    val distanceKm = trackerState.totalDistanceMeters / 1000.0
    val paceSeconds = if (distanceKm > 0.0) (durationSeconds / distanceKm).toLong() else 0L
    val speedKmh = if (durationSeconds > 0L) distanceKm / durationSeconds * 3600.0 else 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LxCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "当前距离",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format("%.2f", distanceKm),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "km",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActiveMetricCard(
                    title = "时长",
                    value = durationSeconds.formatDuration(),
                    modifier = Modifier.weight(1f),
                )
                ActiveMetricCard(
                    title = "配速",
                    value = if (paceSeconds > 0L) paceSeconds.formatPace() else "--'--\"",
                    modifier = Modifier.weight(1f),
                )
                ActiveMetricCard(
                    title = "速度",
                    value = if (speedKmh > 0.0) String.format("%.1f", speedKmh) else "--",
                    unitText = if (speedKmh > 0.0) "km/h" else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            LxCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "定位状态",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = trackerState.locationLabel,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!trackerState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = trackerState.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "已记录 ${trackerState.points.size} 个轨迹点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LxButton(text = "结束并上传", onClick = onFinish)
                    LxOutlinedButton(text = "放弃本次", onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun ActiveMetricCard(
    title: String,
    value: String,
    unitText: String? = null,
    modifier: Modifier = Modifier,
) {
    LxCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (!unitText.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unitText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private fun Long.formatDuration(): String {
    val hours = TimeUnit.SECONDS.toHours(this)
    val minutes = TimeUnit.SECONDS.toMinutes(this) % 60
    val seconds = this % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun Long.formatPace(): String {
    val minutes = this / 60
    val seconds = this % 60
    return String.format("%d'%02d\"", minutes, seconds)
}
