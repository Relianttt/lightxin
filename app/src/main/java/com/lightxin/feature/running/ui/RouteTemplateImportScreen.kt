package com.lightxin.feature.running.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxChoiceChip
import com.lightxin.core.designsystem.component.LxEmpty
import com.lightxin.core.designsystem.component.LxInlineErrorCard
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxRose
import com.lightxin.core.designsystem.theme.LxSage
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.running.domain.ConflictChoice
import com.lightxin.feature.running.domain.ImportedTemplate
import com.lightxin.feature.running.domain.ImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RouteTemplateImportScreen(
    onBack: () -> Unit,
    onImported: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteTemplateViewModel = hiltViewModel(),
) {
    val importState by viewModel.importState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 每次进入导入页从干净状态开始，避免上次解析结果残留
    LaunchedEffect(Unit) { viewModel.resetImport() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
            }.getOrNull()
            if (raw.isNullOrBlank()) {
                viewModel.setImportError("无法读取这个文件")
            } else {
                viewModel.parseImport(raw)
            }
        }
    }

    LaunchedEffect(importState.report) {
        if (importState.report != null) {
            viewModel.consumeImportReport()
            onImported()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "导入模板", onBack = onBack) },
    ) { padding ->
        val preview = importState.preview
        when {
            importState.parseError != null -> Column(
                modifier = Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LxInlineErrorCard(
                    message = "文件解析失败：${importState.parseError}",
                    onRetry = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
                LxOutlinedButton(
                    text = "选择其它文件",
                    onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
            preview == null -> ImportIntro(
                onPick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.padding(padding),
            )
            preview.valid.isEmpty() && preview.rejected.isEmpty() -> LxEmpty(
                message = "文件里没有模板",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> ImportPreviewContent(
                preview = preview,
                decisions = importState.decisions,
                isImporting = importState.isImporting,
                onChoose = viewModel::setConflictChoice,
                onPickAnother = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onConfirm = viewModel::confirmImport,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ImportIntro(
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LxCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "从 JSON 文件导入",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "支持本应用导出的模板文件，也兼容只包含模板数组的 JSON。" +
                        "距离与质量会按轨迹点重新计算，不沿用文件里的旧值。" +
                        "重名或同 ID 的模板会在下一步逐条询问如何处理。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = LxInkMuted,
                )
            }
        }
        LxOutlinedButton(text = "选择 JSON 文件", onClick = onPick)
        Text(
            text = "文件只在本地解析，不会上传。",
            fontSize = 12.sp,
            color = LxInkMuted,
        )
    }
}

@Composable
private fun ImportPreviewContent(
    preview: ImportPreview,
    decisions: Map<String, ConflictChoice>,
    isImporting: Boolean,
    onChoose: (String, ConflictChoice) -> Unit,
    onPickAnother: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LxCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "共 ${preview.totalCount} 条 · 可导入 ${preview.importableCount} 条" +
                            if (preview.rejected.isNotEmpty()) " · 无效 ${preview.rejected.size} 条" else "",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LxInk,
                    )
                    if (preview.conflicts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${preview.conflicts.size} 条与现有模板重名或同 ID，请在下方逐条选择。",
                            fontSize = 12.sp,
                            color = LxTerra,
                        )
                    }
                }
            }
        }

        items(preview.valid, key = { it.fileId }) { item ->
            ImportedItemCard(
                item = item,
                conflict = preview.isConflict(item),
                decision = decisions[item.fileId] ?: ConflictChoice.KEEP_COPY,
                onChoose = { onChoose(item.fileId, it) },
            )
        }

        if (preview.rejected.isNotEmpty()) {
            item {
                Text(
                    text = "不可导入",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxRose,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(preview.rejected.size) { index ->
                val rejected = preview.rejected[index]
                LxCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = rejected.name, fontSize = 14.sp, color = LxInk)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = rejected.reason, fontSize = 12.sp, color = LxRose)
                    }
                }
            }
        }

        item {
            LxButton(
                text = if (isImporting) "正在导入…" else "导入 ${preview.importableCount} 项",
                onClick = onConfirm,
                enabled = preview.importableCount > 0 && !isImporting,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LxOutlinedButton(text = "重新选择文件", onClick = onPickAnother, enabled = !isImporting)
        }
    }
}

@Composable
private fun ImportedItemCard(
    item: ImportedTemplate,
    conflict: Boolean,
    decision: ConflictChoice,
    onChoose: (ConflictChoice) -> Unit,
) {
    LxCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxInk,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (conflict) "有冲突" else "可导入",
                    fontSize = 11.sp,
                    color = if (conflict) LxTerra else LxSage,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format(
                    "%.2f km · %d 个点 · %s",
                    item.totalDistanceMeters / 1000.0,
                    item.points.size,
                    item.qualityStatus.displayName(),
                ),
                fontSize = 12.sp,
                color = LxInkMuted,
            )
            if (conflict) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LxChoiceChip(
                        text = "覆盖",
                        selected = decision == ConflictChoice.OVERWRITE,
                        onClick = { onChoose(ConflictChoice.OVERWRITE) },
                    )
                    LxChoiceChip(
                        text = "保留副本",
                        selected = decision == ConflictChoice.KEEP_COPY,
                        onClick = { onChoose(ConflictChoice.KEEP_COPY) },
                    )
                    LxChoiceChip(
                        text = "跳过",
                        selected = decision == ConflictChoice.SKIP,
                        onClick = { onChoose(ConflictChoice.SKIP) },
                    )
                }
            }
        }
    }
}
