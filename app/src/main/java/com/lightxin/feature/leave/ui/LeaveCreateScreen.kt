package com.lightxin.feature.leave.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxBottomBar
import com.lightxin.core.designsystem.component.LxChoiceGroup
import com.lightxin.core.designsystem.component.LxDateRangeSheet
import com.lightxin.core.designsystem.component.LxField
import com.lightxin.core.designsystem.component.LxInput
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxOptionSheet
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxSelectRow
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTextArea
import com.lightxin.core.designsystem.component.lxHeaderCollapsed
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.core.designsystem.theme.LxTint
import com.lightxin.feature.leave.domain.LeaveAttachment
import com.lightxin.feature.leave.domain.LeaveFormField
import com.lightxin.feature.leave.domain.LeaveFormType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LeaveCreateScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LeaveCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

    var pendingUploadKey by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    fun upload(key: String, uri: Uri) {
        scope.launch {
            val file = copyToCache(context, uri, "leave")
            if (file == null) {
                snackbarHostState.showSnackbar("无法读取所选文件")
            } else {
                viewModel.addAttachment(key, file)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val key = pendingUploadKey
        pendingUploadKey = null
        if (uri != null && key != null) upload(key, uri)
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val key = pendingUploadKey
        pendingUploadKey = null
        if (uri != null && key != null) upload(key, uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val key = pendingUploadKey
        val file = pendingCameraFile
        pendingUploadKey = null
        pendingCameraFile = null
        pendingCameraUri = null
        if (success && file != null && key != null) viewModel.addAttachment(key, file)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingCameraUri
        if (granted && uri != null) {
            runCatching { cameraLauncher.launch(uri) }.onFailure {
                scope.launch { snackbarHostState.showSnackbar("没有可用的相机应用，请从相册选择") }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("需要相机权限才能拍照") }
        }
    }

    fun launchCamera(key: String) {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "leave_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUploadKey = key
        pendingCameraFile = file
        pendingCameraUri = uri
        runCatching { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("没有可用的相机应用，请从相册选择") }
        }
    }

    LaunchedEffect(uiState.submitted) {
        if (uiState.submitted) onSubmitted()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = {
            LxNavBar(onBack = onBack, title = "发起请假", collapsed = collapsed) {
                LxTextAction(
                    text = "暂存",
                    onClick = { viewModel.submit(draft = true) },
                    color = LxTextMuted,
                    enabled = !uiState.isSubmitting,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!uiState.isLoading && uiState.loadError == null) {
                LxBottomBar {
                    LxPrimaryButton(
                        text = if (uiState.isSubmitting) "提交中…" else "提交申请",
                        onClick = { viewModel.submit(draft = false) },
                        enabled = !uiState.isSubmitting && uiState.fields.isNotEmpty(),
                    )
                }
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LxSkeletonList(count = 5, modifier = Modifier.padding(padding))
            uiState.loadError != null -> Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = uiState.loadError.orEmpty(), fontSize = 13.sp, color = LxDanger)
                LxTextAction(text = "重新加载", onClick = viewModel::load, modifier = Modifier.padding(top = 8.dp))
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
            ) {
                val hiddenReadonly = setOf("标题", "申请人", "班级", "编号", "学号")
                // 只调显示顺序：类型 → 起止时间 → 是否离校 → 事由 → 联系人与地址 → 附件。
                // 提交仍按服务端字段原样回传，不受这里的顺序影响。
                val visibleFields = uiState.fields
                    .filterNot { it.label == "标题" || (it.readonly && it.label in hiddenReadonly) }
                    .sortedBy(::displayRank)
                // 说明/审批规则类只读字段收成一个可展开的条目，别让 5 条规则铺在表单前后。
                val noteFields = visibleFields.filter { it.type == LeaveFormType.READONLY_TEXT }
                val inputFields = visibleFields.filterNot { it.type == LeaveFormType.READONLY_TEXT }
                val identity = listOf("申请人", "班级")
                    .mapNotNull { label ->
                        uiState.fields.firstOrNull { it.label == label }?.let { field ->
                            uiState.defaults.get(field.key)?.takeIf { it.isJsonPrimitive }?.asString
                                ?.ifBlank { uiState.values[field.key] }
                        }
                    }
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                item {
                    LxPageHeader(title = "发起请假")
                    if (identity.isNotBlank()) {
                        Text(
                            text = identity,
                            fontSize = 12.sp,
                            color = LxTextMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                itemsIndexed(inputFields, key = { _, field -> field.key }) { _, field ->
                    LeaveFieldBlock(
                        field = field,
                        state = uiState,
                        onValue = viewModel::setValue,
                        onDateRange = viewModel::setDateRange,
                        onPickFile = { key ->
                            pendingUploadKey = key
                            filePicker.launch(arrayOf("*/*"))
                        },
                        onPickImage = { key ->
                            pendingUploadKey = key
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onCamera = ::launchCamera,
                        onRemoveAttachment = viewModel::removeAttachment,
                    )
                }
                if (noteFields.isNotEmpty()) {
                    item(key = "notes") {
                        val texts = noteFields
                            .mapNotNull { field -> readonlyValue(field, uiState) }
                            .filter { it.isNotBlank() }
                        if (texts.isNotEmpty()) {
                            ReadonlyNote(
                                label = if (noteFields.any { it.label.contains("规则") || it.label.contains("说明") }) {
                                    "审批规则与说明"
                                } else {
                                    noteFields.first().label
                                },
                                text = texts.joinToString("\n"),
                            )
                        }
                    }
                }
                if (uiState.fields.isEmpty()) {
                    item {
                        Text(
                            text = "没有拿到表单字段，请返回后重试或检查网络。",
                            fontSize = 13.sp,
                            color = LxDanger,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                item {
                    Text(
                        text = "提交后进入辅导员审批，审批期间可撤回",
                        fontSize = 11.sp,
                        color = LxTextMuted,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaveFieldBlock(
    field: LeaveFormField,
    state: LeaveCreateUiState,
    onValue: (String, String) -> Unit,
    onDateRange: (String, String, String) -> Unit,
    onPickFile: (String) -> Unit,
    onPickImage: (String) -> Unit,
    onCamera: (String) -> Unit,
    onRemoveAttachment: (String, String) -> Unit,
) {
    val error = state.errors.firstOrNull { it.key == field.key }?.message

    when (field.type) {
        LeaveFormType.RADIO -> LxField(
            label = field.label,
            required = field.required,
            error = error,
        ) {
            LxChoiceGroup(
                options = field.options.map { it.label to it.value },
                selectedValue = state.values[field.key],
                onSelect = { value -> onValue(field.key, value) },
            )
        }

        LeaveFormType.SELECT -> LxField(
            label = field.label,
            required = field.required,
            error = error,
        ) {
            LxSelectField(
                value = state.values[field.key].orEmpty(),
                options = field.options.map { it.label to it.value },
                onSelect = { value, _ -> onValue(field.key, value) },
            )
        }

        LeaveFormType.TEXTAREA -> LxField(
            label = field.label,
            required = field.required,
            error = error,
        ) {
            LxTextArea(
                value = state.values[field.key].orEmpty(),
                onValueChange = { onValue(field.key, it) },
                placeholder = "说明原因，便于辅导员快速判断",
                enabled = !field.readonly,
            )
        }

        LeaveFormType.DATE_RANGE -> LxField(
            label = field.label,
            required = field.required,
            error = error,
        ) {
            DateRangeField(
                range = state.dateRanges[field.key],
                onRange = { start, end -> onDateRange(field.key, start, end) },
            )
        }

        LeaveFormType.UPLOAD_FILE, LeaveFormType.UPLOAD_IMG -> LxField(
            label = field.label,
            required = field.required,
            error = error,
        ) {
            AttachmentField(
                field = field,
                files = state.attachments[field.key].orEmpty(),
                uploading = state.uploadingKeys.contains(field.key),
                onPickFile = { onPickFile(field.key) },
                onPickImage = { onPickImage(field.key) },
                onCamera = { onCamera(field.key) },
                onRemove = { uid -> onRemoveAttachment(field.key, uid) },
            )
        }

        LeaveFormType.FORMULA -> {
            val computed = if (field.key == "QJSC") {
                val range = state.dateRanges["QJRQ"]
                val start = range?.first?.let(::parseDateTime)
                val end = range?.second?.let(::parseDateTime)
                if (start != null && end != null && end.isAfter(start)) {
                    val minutes = java.time.Duration.between(start, end).toMinutes()
                    val hours = minutes / 60.0
                    if (hours % 1.0 == 0.0) "${hours.toInt()} 小时" else String.format(java.util.Locale.US, "%.1f 小时", hours)
                } else null
            } else null
            val value = computed
                ?: state.defaults.get(field.key)?.takeIf { it.isJsonPrimitive }?.asString
            if (!value.isNullOrBlank()) {
                ReadonlyRow(field.label, value)
            }
        }

        LeaveFormType.READONLY_TEXT -> {
            val value = readonlyValue(field, state)
            if (value.isNotBlank()) {
                ReadonlyNote(label = field.label.ifBlank { "审批规则" }, text = value)
            }
        }

        LeaveFormType.ADDRESS, LeaveFormType.TEXT_INPUT -> {
            val value = state.values[field.key].orEmpty().ifBlank {
                state.defaults.get(field.key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            }
            if (field.readonly) {
                ReadonlyRow(field.label, value)
            } else {
                LxField(
                    label = field.label,
                    required = field.required,
                    error = error,
                ) {
                    LxInput(
                        value = value,
                        onValueChange = { onValue(field.key, it) },
                        placeholder = "请输入${field.label}",
                    )
                }
            }
        }

        LeaveFormType.UNKNOWN -> LxField(
            label = "${field.label}（未识别类型）",
            required = field.required,
            error = error,
        ) {
            LxInput(
                value = state.values[field.key].orEmpty(),
                onValueChange = { onValue(field.key, it) },
                placeholder = "请输入",
                enabled = !field.readonly,
            )
        }
    }
}

@Composable
private fun ReadonlyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 13.sp, color = LxTextMuted)
        Text(text = value, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = LxText)
    }
}

@Composable
private fun LxSelectField(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (label: String, value: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.second == value }?.first ?: value
    LxSelectRow(
        value = display,
        placeholder = "请选择",
        onClick = { expanded = true },
    )
    if (expanded) {
        LxOptionSheet(
            title = "请选择",
            options = options,
            selectedValue = value,
            onSelect = onSelect,
            onDismiss = { expanded = false },
        )
    }
}

@Composable
private fun DateRangeField(
    range: Pair<String, String>?,
    onRange: (String, String) -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    val start = range?.first.orEmpty()
    val end = range?.second.orEmpty()
    val display = if (start.isBlank() && end.isBlank()) {
        ""
    } else {
        formatRangeLabel(start) + "  →  " + formatRangeLabel(end)
    }

    LxSelectRow(
        value = display,
        placeholder = "选择起止时间",
        onClick = { sheetVisible = true },
    )

    if (sheetVisible) {
        LxDateRangeSheet(
            initialStart = parseDateTime(start),
            initialEnd = parseDateTime(end),
            onConfirm = { pickedStart, pickedEnd ->
                onRange(pickedStart.format(DATE_FORMAT), pickedEnd.format(DATE_FORMAT))
                sheetVisible = false
            },
            onDismiss = { sheetVisible = false },
        )
    }
}

@Composable
private fun AttachmentField(
    field: LeaveFormField,
    files: List<LeaveAttachment>,
    uploading: Boolean,
    onPickFile: () -> Unit,
    onPickImage: () -> Unit,
    onCamera: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LxTint)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = LxAccent,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                )
                Text(
                    text = file.fileName.ifBlank { "附件" },
                    fontSize = 13.sp,
                    color = LxText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除",
                    tint = LxTextMuted,
                    modifier = Modifier.size(18.dp).clickable { onRemove(file.uid) },
                )
            }
        }
        if (uploading) {
            Text(text = "上传中…", fontSize = 12.sp, color = LxAccent)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LxTint)
                    .clickable(enabled = !uploading) {
                        if (field.type == LeaveFormType.UPLOAD_IMG) onPickImage() else onPickFile()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加附件", tint = LxTextMuted)
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = if (field.type == LeaveFormType.UPLOAD_IMG) "拍照或从相册选择" else "选择文件",
                    fontSize = 12.5.sp,
                    color = LxTextMuted,
                )
                if (field.type == LeaveFormType.UPLOAD_IMG) {
                    LxTextAction(
                        text = "拍照",
                        onClick = onCamera,
                        enabled = !uploading,
                    )
                }
            }
        }
        Text(
            text = if (field.required) "必填附件，单个文件不超过 10MB" else "可选附件，单个文件不超过 10MB",
            fontSize = 11.sp,
            color = LxTextMuted,
        )
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatRangeLabel(raw: String): String {
    val parsed = parseDateTime(raw) ?: return raw
    val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[parsed.dayOfWeek.value - 1]
    return "%02d/%02d %s %02d:%02d".format(
        parsed.monthValue,
        parsed.dayOfMonth,
        week,
        parsed.hour,
        parsed.minute,
    )
}

private fun parseDateTime(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw, DATE_FORMAT) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()
        ?: runCatching {
            java.time.LocalDate.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()
        }.getOrNull()

private suspend fun copyToCache(context: Context, uri: Uri, prefix: String): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.dat")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file
        }.getOrNull()
    }

private fun readonlyValue(field: LeaveFormField, state: LeaveCreateUiState): String =
    field.defaultValue.orEmpty().ifBlank {
        state.values[field.key].orEmpty().ifBlank {
            state.defaults.get(field.key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        }
    }

/** 显示顺序用的分组：0 类型、1 起止时间、2 时长、3 是否离校、4 事由、5 联系人与地址、6 附件、7 规则说明。 */
private fun displayRank(field: LeaveFormField): Int {
    val label = field.label
    return when {
        field.type == LeaveFormType.DATE_RANGE -> 1
        field.type == LeaveFormType.FORMULA -> 2
        label.contains("离校") || label.contains("外出") || label.contains("住宿") -> 3
        field.type == LeaveFormType.TEXTAREA || label.contains("事由") || label.contains("原因") -> 4
        label.contains("类型") -> 0
        field.type == LeaveFormType.UPLOAD_FILE || field.type == LeaveFormType.UPLOAD_IMG -> 6
        field.type == LeaveFormType.READONLY_TEXT -> 7
        else -> 5
    }
}

/** 服务端下发的长说明（审批规则等）默认收成一行，需要时再展开，不占满首屏。 */
@Composable
private fun ReadonlyNote(label: String, text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = LxTextMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "收起" else "展开",
                fontSize = 12.sp,
                color = LxAccent,
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = LxTextMuted,
                modifier = Modifier.padding(start = 2.dp).size(18.dp),
            )
        }
        if (expanded) {
            Text(
                text = text,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
