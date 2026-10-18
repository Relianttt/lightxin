package com.lightxin.feature.leave.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxTextField
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxCream
import com.lightxin.core.designsystem.theme.LxError
import com.lightxin.core.designsystem.theme.LxInk
import com.lightxin.core.designsystem.theme.LxInkMuted
import com.lightxin.core.designsystem.theme.LxSandDeep
import com.lightxin.core.designsystem.theme.LxTerra
import com.lightxin.feature.leave.domain.LeaveAttachment
import com.lightxin.feature.leave.domain.LeaveFormField
import com.lightxin.feature.leave.domain.LeaveFormType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
            cameraLauncher.launch(uri)
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
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "发起请假", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            uiState.loadError != null -> Column(
                modifier = Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = uiState.loadError.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LxError,
                )
                LxOutlinedButton(text = "重新加载", onClick = viewModel::load)
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        text = "表单由学校服务端定义，字段顺序与必填项以服务端为准。",
                        fontSize = 12.sp,
                        color = LxInkMuted,
                    )
                }
                if (uiState.fields.isEmpty()) {
                    item {
                        Text(
                            text = "没有拿到表单字段，请返回后重试或检查网络。",
                            fontSize = 13.sp,
                            color = LxError,
                        )
                    }
                }
                items(uiState.fields.size) { index ->
                    val field = uiState.fields[index]
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
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    LxButton(
                        text = if (uiState.isSubmitting) "提交中…" else "提交",
                        onClick = { viewModel.submit(draft = false) },
                        enabled = !uiState.isSubmitting && uiState.fields.isNotEmpty(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LxOutlinedButton(
                        text = "暂存",
                        onClick = { viewModel.submit(draft = true) },
                        enabled = !uiState.isSubmitting,
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
    val label = buildString {
        append(field.label)
        if (field.required) append(" *")
    }

    when (field.type) {
        LeaveFormType.RADIO -> Column {
            FieldLabel(label, error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.forEach { option ->
                    com.lightxin.core.designsystem.component.LxChoiceChip(
                        text = option.label,
                        selected = state.values[field.key] == option.value,
                        onClick = { onValue(field.key, option.value) },
                    )
                }
            }
            FieldError(error)
        }

        LeaveFormType.SELECT -> Column {
            FieldLabel(label, error)
            LxSelectField(
                value = state.values[field.key].orEmpty(),
                options = field.options.map { it.label to it.value },
                onSelect = { value, _ -> onValue(field.key, value) },
            )
            FieldError(error)
        }

        LeaveFormType.TEXTAREA -> Column {
            LxTextField(
                value = state.values[field.key].orEmpty(),
                onValueChange = { onValue(field.key, it) },
                label = label,
                singleLine = false,
                enabled = !field.readonly,
            )
            FieldError(error)
        }

        LeaveFormType.DATE_RANGE -> Column {
            FieldLabel(label, error)
            DateRangeField(
                range = state.dateRanges[field.key],
                onRange = { start, end -> onDateRange(field.key, start, end) },
            )
            FieldError(error)
        }

        LeaveFormType.UPLOAD_FILE, LeaveFormType.UPLOAD_IMG -> Column {
            FieldLabel(label, error)
            AttachmentField(
                field = field,
                files = state.attachments[field.key].orEmpty(),
                uploading = state.uploadingKeys.contains(field.key),
                onPickFile = { onPickFile(field.key) },
                onPickImage = { onPickImage(field.key) },
                onCamera = { onCamera(field.key) },
                onRemove = { uid -> onRemoveAttachment(field.key, uid) },
            )
            FieldError(error)
        }

        LeaveFormType.FORMULA -> {
            // 只读计算项：有日期区间时本地先展示估算值，提交时由 Builder 统一重算
            val computed = if (field.key == "QJSC") {
                val range = state.dateRanges["QJRQ"]
                val start = range?.first?.let(::parseDateTime)
                val end = range?.second?.let(::parseDateTime)
                if (start != null && end != null && end.isAfter(start)) {
                    val minutes = java.time.Duration.between(start, end).toMinutes()
                    val hours = minutes / 60.0
                    if (hours % 1.0 == 0.0) "${hours.toInt()} 小时" else String.format("%.1f 小时", hours)
                } else {
                    null
                }
            } else {
                null
            }
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
                    lineHeight = 18.sp,
                    color = LxInkMuted,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }

        LeaveFormType.ADDRESS, LeaveFormType.TEXT_INPUT -> Column {
            LxTextField(
                value = state.values[field.key].orEmpty(),
                onValueChange = { onValue(field.key, it) },
                label = label,
                enabled = !field.readonly,
            )
            FieldError(error)
        }

        LeaveFormType.UNKNOWN -> {
            // 未知字段不丢：可编辑成字符串提交
            Column {
                LxTextField(
                    value = state.values[field.key].orEmpty(),
                    onValueChange = { onValue(field.key, it) },
                    label = "$label（未识别类型）",
                    enabled = !field.readonly,
                )
                FieldError(error)
            }
        }
    }
}

@Composable
private fun FieldLabel(label: String, error: String?) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (error != null) LxError else LxInkMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun FieldError(error: String?) {
    if (error != null) {
        Text(
            text = error,
            fontSize = 12.sp,
            color = LxError,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}

@Composable
private fun ReadonlyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = LxInkMuted)
        Text(text = value, fontSize = 13.sp, color = LxInk)
    }
}

@Composable
private fun LxSelectField(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (label: String, value: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.second == value }?.first ?: value.ifBlank { "请选择" }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MaterialTheme.shapes.small)
                .background(LxCream)
                .border(1.dp, LxSandDeep, MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = display,
                fontSize = 14.sp,
                color = if (value.isBlank()) LxInkMuted else LxInk,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = LxInkMuted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, optionValue) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(label, optionValue)
                    },
                )
            }
        }
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
        LeaveDateTimePicker(
            initial = parseDateTime(start) ?: LocalDateTime.now(),
            onConfirm = { picked ->
                val formatted = picked.format(DATE_FORMAT)
                val nextEnd = if (end.isBlank()) {
                    picked.plusHours(2).format(DATE_FORMAT)
                } else {
                    end
                }
                onRange(formatted, nextEnd)
                editingStart = false
            },
            onDismiss = { editingStart = false },
        )
    }
    if (editingEnd) {
        LeaveDateTimePicker(
            initial = parseDateTime(end) ?: parseDateTime(start) ?: LocalDateTime.now(),
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
        Text(
            text = label,
            fontSize = 11.sp,
            color = LxInkMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(LxCream)
                .border(1.dp, LxSandDeep, MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Text(
                text = value.ifBlank { "选择时间" },
                fontSize = 13.sp,
                color = if (value.isBlank()) LxInkMuted else LxInk,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveDateTimePicker(
    initial: LocalDateTime,
    onConfirm: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var stage by remember { mutableIntStateOf(0) }
    var pickedDate by remember { mutableStateOf(initial.toLocalDate()) }

    if (stage == 0) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        pickedDate = LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    stage = 1
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(
                        LocalDateTime.of(
                            pickedDate,
                            LocalTime.of(timeState.hour, timeState.minute),
                        ),
                    )
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
                    .clip(MaterialTheme.shapes.small)
                    .background(LxCream)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.UploadFile,
                    contentDescription = null,
                    tint = LxTerra,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = file.fileName.ifBlank { "附件" },
                    fontSize = 13.sp,
                    color = LxInk,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "移除",
                    tint = LxInkMuted,
                    modifier = Modifier.clickable { onRemove(file.uid) },
                )
            }
        }
        if (uploading) {
            Text(text = "上传中…", fontSize = 12.sp, color = LxTerra)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (field.type == LeaveFormType.UPLOAD_IMG) {
                LxOutlinedButton(
                    text = "相册",
                    onClick = onPickImage,
                    modifier = Modifier.weight(1f),
                    enabled = !uploading,
                )
                LxOutlinedButton(
                    text = "拍照",
                    onClick = onCamera,
                    modifier = Modifier.weight(1f),
                    enabled = !uploading,
                )
            } else {
                LxOutlinedButton(
                    text = "选择文件",
                    onClick = onPickFile,
                    modifier = Modifier.weight(1f),
                    enabled = !uploading,
                )
            }
        }
        Text(
            text = if (field.required) "必填附件，单个文件不超过 10MB" else "可选附件，单个文件不超过 10MB",
            fontSize = 11.sp,
            color = LxInkMuted,
        )
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun parseDateTime(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw, DATE_FORMAT) }.getOrNull()

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
