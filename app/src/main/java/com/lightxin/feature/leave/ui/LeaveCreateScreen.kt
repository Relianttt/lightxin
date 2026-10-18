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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
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
import com.lightxin.core.designsystem.component.LxDateTimeSheet
import com.lightxin.core.designsystem.component.LxField
import com.lightxin.core.designsystem.component.LxInput
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxOptionSheet
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPill
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxSectionLabel
import com.lightxin.core.designsystem.component.LxSelectRow
import com.lightxin.core.designsystem.component.LxSkeletonList
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTextArea
import com.lightxin.core.designsystem.component.formatLxDateTime
import com.lightxin.core.designsystem.component.parseLxDateTime
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
            LxNavBar(onBack = onBack) {
                LxTextAction(
                    text = "暂存",
                    onClick = { viewModel.submit(draft = true) },
                    color = LxTextMuted,
                    enabled = !uiState.isSubmitting,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
            ) {
                val hiddenReadonly = setOf("标题", "申请人", "班级", "编号", "学号")
                val visibleFields = uiState.fields.filterNot {
                    it.label == "标题" || (it.readonly && it.label in hiddenReadonly)
                }
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
                    LxPageHeader(title = "发起请假", subtitle = "字段由学校服务端下发")
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
                itemsIndexed(visibleFields, key = { _, field -> field.key }) { _, field ->
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
                    Spacer(Modifier.height(12.dp))
                    LxPrimaryButton(
                        text = if (uiState.isSubmitting) "提交中…" else "提交申请",
                        onClick = { viewModel.submit(draft = false) },
                        enabled = !uiState.isSubmitting && uiState.fields.isNotEmpty(),
                    )
                    Text(
                        text = "提交后进入辅导员审批，审批期间可撤回",
                        fontSize = 11.sp,
                        color = LxTextMuted,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            LxPill(
                                text = option.label,
                                selected = state.values[field.key] == option.value,
                                onClick = { onValue(field.key, option.value) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
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
                    if (hours % 1.0 == 0.0) "${hours.toInt()} 小时" else String.format("%.1f 小时", hours)
                } else null
            } else null
            val value = computed
                ?: state.defaults.get(field.key)?.takeIf { it.isJsonPrimitive }?.asString
            if (!value.isNullOrBlank()) {
                ReadonlyRow(field.label, value)
            }
        }

        LeaveFormType.READONLY_TEXT -> {
            val value = field.defaultValue.orEmpty().ifBlank {
                state.values[field.key].orEmpty().ifBlank {
                    state.defaults.get(field.key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                }
            }
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = LxTextMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
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
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    val start = range?.first.orEmpty()
    val end = range?.second.orEmpty()

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateSlot(
            label = "开始",
            value = start,
            modifier = Modifier.weight(1f),
            onClick = { editingStart = true },
        )
        DateSlot(
            label = "结束",
            value = end,
            modifier = Modifier.weight(1f),
            onClick = { editingEnd = true },
        )
    }

    if (editingStart) {
        LxDateTimeSheet(
            initial = parseDateTime(start) ?: LocalDateTime.now(),
            title = "选择开始时间",
            onConfirm = { picked ->
                val formatted = picked.format(DATE_FORMAT)
                val nextEnd = if (end.isBlank()) picked.plusHours(2).format(DATE_FORMAT) else end
                onRange(formatted, nextEnd)
                editingStart = false
            },
            onDismiss = { editingStart = false },
        )
    }
    if (editingEnd) {
        LxDateTimeSheet(
            initial = parseDateTime(end) ?: parseDateTime(start) ?: LocalDateTime.now(),
            title = "选择结束时间",
            onConfirm = { picked ->
                onRange(start, picked.format(DATE_FORMAT))
                editingEnd = false
            },
            onDismiss = { editingEnd = false },
        )
    }
}

@Composable
private fun DateSlot(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        LxSelectRow(
            value = value.let { raw ->
                parseDateTime(raw)?.let { formatShort(it) } ?: raw
            },
            placeholder = label,
            onClick = onClick,
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
private val SHORT_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm")

private fun formatShort(value: LocalDateTime): String = value.format(SHORT_FORMAT)

private fun parseDateTime(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw, DATE_FORMAT) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()

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
