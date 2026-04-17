package com.lightxin.feature.running.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxInkSoft
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.core.designsystem.theme.LxTerraSoft
import com.lightxin.feature.running.domain.RouteTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteTemplateListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var actionTarget by remember { mutableStateOf<RouteTemplate?>(null) }
    var renameTarget by remember { mutableStateOf<RouteTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<RouteTemplate?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState()

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
                        onClick = { actionTarget = template },
                    )
                }
            }
        }
    }

    if (actionTarget != null) {
        val t = actionTarget!!
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(
                    text = t.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                ActionRow(
                    icon = Icons.Outlined.Star,
                    label = if (t.isDefault) "已是默认模板" else "设为默认",
                    enabled = !t.isDefault,
                    onClick = {
                        viewModel.setDefault(t.id)
                        actionTarget = null
                    },
                )
                ActionRow(
                    icon = Icons.Outlined.DriveFileRenameOutline,
                    label = "重命名",
                    onClick = {
                        renameInput = t.name
                        renameTarget = t
                        actionTarget = null
                    },
                )
                ActionRow(
                    icon = Icons.Outlined.Delete,
                    label = "删除",
                    tint = LxRose,
                    onClick = {
                        deleteTarget = t
                        actionTarget = null
                    },
                )
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("重命名模板") },
            text = {
                LxTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = "模板名称",
                    keyboardOptions = KeyboardOptions.Default,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(renameTarget!!.id, renameInput)
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("删除模板") },
            text = { Text("确定删除「${deleteTarget!!.name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(deleteTarget!!.id)
                    deleteTarget = null
                }) { Text("删除", color = LxRose) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
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
                if (template.isDefault) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LxTerraSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "默认",
                            fontSize = 11.sp,
                            color = LxTerra,
                            fontWeight = FontWeight.Medium,
                        )
                    }
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
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = LxInkSoft,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 20.dp, vertical = 14.dp)
    Row(
        modifier = base,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = label, fontSize = 15.sp, color = if (enabled) LxInk else LxInkMuted)
    }
}
