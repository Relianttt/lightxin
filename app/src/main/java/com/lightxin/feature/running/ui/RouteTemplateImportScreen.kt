package com.lightxin.feature.running.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxEmptyState
import com.lightxin.core.designsystem.component.LxGhostButton
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTone
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxHairline
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
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
    val preview = importState.preview

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

    fun pickFile() {
        picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack) {
                if (preview != null || importState.parseError != null) {
                    LxTextAction(
                        text = "重新选择",
                        onClick = ::pickFile,
                        color = LxTextMuted,
                    )
                }
            }
        },
    ) { padding ->
        when {
            importState.parseError != null -> Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "文件解析失败",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LxText,
                )
                Text(
                    text = importState.parseError.orEmpty(),
                    fontSize = 12.5.sp,
                    lineHeight = 21.sp,
                    color = LxTextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                LxPrimaryButton(
                    text = "选择其它文件",
                    onClick = ::pickFile,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            preview == null -> ImportIntro(
                onPick = ::pickFile,
                modifier = Modifier.padding(padding),
            )
            preview.valid.isEmpty() && preview.rejected.isEmpty() -> Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LxEmptyState(
                    title = "文件里没有模板",
                    body = "确认这是本应用导出的 JSON，或换一个文件试试。",
                )
            }
            else -> ImportPreviewContent(
                preview = preview,
                decisions = importState.decisions,
                isImporting = importState.isImporting,
                onChoose = viewModel::setConflictChoice,
                onPickAnother = ::pickFile,
                onConfirm = viewModel::confirmImport,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ImportIntro(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        LxPageHeader(title = "导入模板", subtitle = "从其它设备导出的 JSON，可含多个模板")
        Text(
            text = "支持本应用导出的模板文件，也兼容只包含模板数组的 JSON。距离与质量会按轨迹点重新计算，不沿用文件里的旧值；重名或同 ID 的模板会在下一步逐条询问。",
            fontSize = 13.sp,
            lineHeight = 23.sp,
            color = LxTextMuted,
            modifier = Modifier.padding(top = 24.dp),
        )
        LxGhostButton(
            text = "选择 JSON 文件",
            onClick = onPick,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "文件只在本地解析，不会上传。",
            fontSize = 11.5.sp,
            color = LxTextMuted,
            modifier = Modifier.padding(top = 12.dp),
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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
    ) {
        item {
            LxPageHeader(title = "导入模板")
            Spacer(Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LxTint)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "已选择文件", fontSize = 13.sp, color = LxTextMuted)
                Text(
                    text = "可导入 ${preview.importableCount} 个",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = LxText,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            LxSectionLabel("解析结果")
            Text(
                text = "读到 ${preview.totalCount} 个模板，其中 ${preview.conflicts.size} 个与现有模板重名。",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = LxText,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(26.dp))
        }

        items(preview.valid, key = { it.fileId }) { item ->
            ImportedItem(
                item = item,
                conflict = preview.isConflict(item),
                decision = decisions[item.fileId] ?: ConflictChoice.KEEP_COPY,
                onChoose = { onChoose(item.fileId, it) },
            )
            Spacer(Modifier.height(18.dp))
        }

        if (preview.rejected.isNotEmpty()) {
            item {
                LxSectionLabel("未通过校验")
                Spacer(Modifier.height(8.dp))
                preview.rejected.forEach { rejected ->
                    Text(
                        text = "“${rejected.name}” ${rejected.reason}",
                        fontSize = 12.5.sp,
                        lineHeight = 21.sp,
                        color = LxDanger,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        item {
            LxPrimaryButton(
                text = if (isImporting) "正在导入…" else "导入 ${preview.importableCount} 个模板",
                onClick = onConfirm,
                enabled = preview.importableCount > 0 && !isImporting,
            )
            LxGhostButton(
                text = "重新选择文件",
                onClick = onPickAnother,
                enabled = !isImporting,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ImportedItem(
    item: ImportedTemplate,
    conflict: Boolean,
    decision: ConflictChoice,
    onChoose: (ConflictChoice) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (conflict) "有冲突" else "可导入",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (conflict) LxAccent else LxTextMuted,
            )
        }
        Text(
            text = String.format(
                "%.2f km · %d 个点 · %s",
                item.totalDistanceMeters / 1000.0,
                item.points.size,
                item.qualityStatus.displayName(),
            ),
            fontSize = 12.5.sp,
            color = LxTextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (conflict) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ConflictChoiceRow(
                    title = "保留两者",
                    description = "新模板自动加后缀“(1)”，两份都留下",
                    selected = decision == ConflictChoice.KEEP_COPY,
                    onClick = { onChoose(ConflictChoice.KEEP_COPY) },
                )
                ConflictChoiceRow(
                    title = "覆盖现有",
                    description = "用文件里的版本替换本地同名模板",
                    selected = decision == ConflictChoice.OVERWRITE,
                    onClick = { onChoose(ConflictChoice.OVERWRITE) },
                )
                ConflictChoiceRow(
                    title = "跳过这一个",
                    description = "只导入其余模板，本地不变",
                    selected = decision == ConflictChoice.SKIP,
                    onClick = { onChoose(ConflictChoice.SKIP) },
                )
            }
        }
    }
}

@Composable
private fun ConflictChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) LxTint else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(LxAccent)
                    } else {
                        Modifier.border(1.dp, LxTextMuted.copy(alpha = 0.55f), CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(LxCanvas))
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = LxText,
            )
            Text(
                text = description,
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
