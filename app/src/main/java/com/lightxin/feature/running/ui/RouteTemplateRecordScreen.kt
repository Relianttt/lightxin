package com.lightxin.feature.running.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDialog
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.running.service.RunTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun RouteTemplateRecordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSaveDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                viewModel.clearError()
                if (viewModel.beginRecording()) {
                    RunTrackingService.start(context)
                }
            } else {
                viewModel.setError("缺少定位权限，无法开始录制")
            }
        },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "录制模板", onBack = onBack) },
    ) { padding ->
        val tracker = uiState.trackerState
        val isRecording = uiState.isRecording

        var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(tracker.startTimeMillis, tracker.isSessionActive) {
            while (tracker.isSessionActive) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
        val durationSeconds =
            if (tracker.isSessionActive) ((nowMillis - tracker.startTimeMillis) / 1000L).coerceAtLeast(0L)
            else 0L
        val distanceKm = tracker.totalDistanceMeters / 1000.0

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LxCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "已录制距离",
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
                    MetricCell(
                        title = "时长",
                        value = durationSeconds.formatDurationTpl(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCell(
                        title = "轨迹点",
                        value = "${tracker.points.size}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCell(
                        title = "定位",
                        value = tracker.locationLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                LxCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "录制须知",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "保存条件：轨迹点需大于 20 个且距离需大于 1 km。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!tracker.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tracker.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            !isRecording -> LxButton(
                                text = "开始录制",
                                onClick = {
                                    val hasLocation = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                        || ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                    if (hasLocation) {
                                        viewModel.clearError()
                                        if (viewModel.beginRecording()) {
                                            RunTrackingService.start(context)
                                        }
                                    } else {
                                        locationPermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            )
                                        )
                                    }
                                },
                                enabled = !uiState.isRealRunActive,
                            )
                            else -> {
                                LxButton(
                                    text = "结束并保存",
                                    onClick = {
                                        RunTrackingService.stop(context)
                                        viewModel.stopCollecting()
                                        nameInput = "模板 ${uiState.templates.size + 1}"
                                        showSaveDialog = true
                                    },
                                )
                                LxOutlinedButton(
                                    text = "放弃本次",
                                    onClick = {
                                        RunTrackingService.cancel(context)
                                        viewModel.cancelRecording()
                                        onBack()
                                    },
                                )
                            }
                        }
                        if (uiState.isRealRunActive && !isRecording) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "当前有跑步会话进行中，结束后再录制模板",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        LxDialog(
            title = "保存模板",
            confirmText = "保存",
            dismissText = "放弃",
            onDismissRequest = { },
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            onDismiss = {
                showSaveDialog = false
                viewModel.cancelRecording()
                onBack()
            },
            onConfirm = {
                scope.launch {
                    val ok = viewModel.saveRecording(nameInput)
                    showSaveDialog = false
                    if (ok) {
                        onBack()
                    }
                }
            },
            content = {
                Column {
                    Text("请为本次路线命名", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    LxTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = "模板名称",
                        keyboardOptions = KeyboardOptions.Default,
                    )
                }
            },
        )
    }
}

@Composable
private fun MetricCell(title: String, value: String, modifier: Modifier = Modifier) {
    LxCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun Long.formatDurationTpl(): String {
    val hours = TimeUnit.SECONDS.toHours(this)
    val minutes = TimeUnit.SECONDS.toMinutes(this) % 60
    val seconds = this % 60
    return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}
