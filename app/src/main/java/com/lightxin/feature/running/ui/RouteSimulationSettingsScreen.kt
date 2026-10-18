package com.lightxin.feature.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxMenuDivider
import com.lightxin.core.designsystem.component.LxMenuRow
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkGhost
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxSand
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RouteSimulationSettingsScreen(
    onBack: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenList: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pendingSaveMessage) {
        uiState.pendingSaveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "路线模拟", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "录制真实校园路线，作为后续模拟提交的底稿。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LxCard {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        StatRow(label = "默认模板", value = uiState.defaultTemplate?.name ?: "未设置")
                        Spacer(modifier = Modifier.height(10.dp))
                        StatRow(label = "模板数量", value = "${uiState.templates.size} 个")
                        Spacer(modifier = Modifier.height(10.dp))
                        StatRow(
                            label = "最近录制",
                            value = uiState.lastRecordedAtMillis?.let { formatDate(it) } ?: "--",
                        )
                    }
                }
            }
            item {
                LxCard {
                    Column {
                        RouteMenuRow(
                            icon = Icons.Outlined.FiberManualRecord,
                            title = "录制新模板",
                            onClick = onOpenRecord,
                        )
                        MenuDivider()
                        RouteMenuRow(
                            icon = Icons.Outlined.UploadFile,
                            title = "导入模板",
                            onClick = onOpenImport,
                        )
                        MenuDivider()
                        RouteMenuRow(
                            icon = Icons.Outlined.FormatListBulleted,
                            title = "管理模板",
                            hint = if (uiState.templates.isEmpty()) "暂无" else "${uiState.templates.size} 个",
                            onClick = onOpenList,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 14.sp, color = LxInkMuted)
        Text(text = value, fontSize = 14.sp, color = LxInk, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun RouteMenuRow(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    onClick: () -> Unit,
) {
    LxMenuRow(
        icon = icon,
        title = title,
        hint = hint,
        hintColor = LxInkMuted,
        onClick = onClick,
    )
}

@Composable
internal fun MenuDivider() {
    LxMenuDivider()
}

internal fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
