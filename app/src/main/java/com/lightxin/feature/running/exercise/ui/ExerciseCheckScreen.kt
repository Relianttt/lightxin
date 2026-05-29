package com.lightxin.feature.running.exercise.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxTopBar

@Composable
fun ExerciseCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseCheckViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "锻炼考勤", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.phase) {
                CheckPhase.POLLING -> {
                    if (uiState.qrContent.isNotBlank()) {
                        val image = remember(uiState.qrContent) { qrImageBitmap(uiState.qrContent) }
                        Image(
                            bitmap = image,
                            contentDescription = "锻炼考勤二维码",
                            modifier = Modifier.size(240.dp),
                        )
                    }
                    Text(
                        text = "请到固定点位打卡机扫描此二维码",
                        modifier = Modifier.padding(top = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CheckPhase.SUCCESS -> Text(
                    text = "打卡成功",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                CheckPhase.TIMEOUT -> {
                    Text(
                        text = "等待超时，未检测到打卡结果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LxButton(
                        text = "重试",
                        onClick = viewModel::retry,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}
