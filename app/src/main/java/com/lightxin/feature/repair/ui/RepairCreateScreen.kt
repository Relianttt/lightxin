package com.lightxin.feature.repair.ui

import android.Manifest
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxChoiceChip
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
import com.lightxin.feature.repair.domain.RepairNode
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RepairCreateScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepairCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val albumLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            copyRepairImage(context, uri)?.let(viewModel::addImage)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        pendingCameraUri = null
        if (success && file != null) viewModel.addImage(file)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val uri = pendingCameraUri
        if (granted && uri != null) {
            cameraLauncher.launch(uri)
        } else {
            scope.launch { snackbarHostState.showSnackbar("需要相机权限才能拍照") }
        }
    }

    LaunchedEffect(uiState.submittedBxdh) {
        if (uiState.submittedBxdh != null) {
            viewModel.consumeSubmitted()
            onSubmitted()
        }
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
        topBar = { LxTopBar(title = "新建报修", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            LxLoading(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        if (uiState.loadError != null) {
            Column(
                modifier = Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = uiState.loadError.orEmpty(), color = LxError, fontSize = 14.sp)
                LxOutlinedButton(text = "重新加载", onClick = viewModel::load)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LxCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "报修地点",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LxInk,
                        )
                        RepairDropdown(
                            label = "校区",
                            value = uiState.campusName,
                            nodes = uiState.campusNodes,
                            onSelect = viewModel::selectCampus,
                        )
                        RepairDropdown(
                            label = "楼栋",
                            value = uiState.buildingName,
                            nodes = uiState.buildingNodes,
                            onSelect = viewModel::selectBuilding,
                        )
                        RepairDropdown(
                            label = "房间",
                            value = uiState.roomName,
                            nodes = uiState.roomNodes,
                            onSelect = viewModel::selectRoom,
                        )
                        uiState.errors["location"]?.let {
                            Text(text = it, color = LxError, fontSize = 12.sp)
                        }
                        LxTextField(
                            value = uiState.xxdz,
                            onValueChange = viewModel::setXxdz,
                            label = "详细地址（如 A36M214）*",
                        )
                        uiState.errors["xxdz"]?.let { Text(text = it, color = LxError, fontSize = 12.sp) }
                    }
                }
            }

            item {
                LxCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "报修类型",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LxInk,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.typeRoots, key = { it.value }) { node ->
                                LxChoiceChip(
                                    text = node.name,
                                    selected = uiState.typeRoot == node.value,
                                    onClick = { viewModel.selectTypeRoot(node.value) },
                                )
                            }
                        }
                        if (uiState.typeChildren.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.typeChildren, key = { it.value }) { node ->
                                    LxChoiceChip(
                                        text = node.name,
                                        selected = uiState.bxlx == node.value,
                                        onClick = { viewModel.selectType(node.value) },
                                    )
                                }
                            }
                        }
                        uiState.errors["bxlx"]?.let { Text(text = it, color = LxError, fontSize = 12.sp) }
                    }
                }
            }

            item {
                LxCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "联系方式",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LxInk,
                        )
                        if (uiState.xm.isNotBlank()) {
                            Text(text = "报修人 ${uiState.xm}", fontSize = 12.sp, color = LxInkMuted)
                        }
                        LxTextField(
                            value = uiState.sjh,
                            onValueChange = viewModel::setPhone,
                            label = "手机号 *",
                        )
                        uiState.errors["sjh"]?.let { Text(text = it, color = LxError, fontSize = 12.sp) }
                        LxTextField(
                            value = uiState.gzms,
                            onValueChange = viewModel::setGzms,
                            label = "故障描述 *",
                            singleLine = false,
                        )
                        uiState.errors["gzms"]?.let { Text(text = it, color = LxError, fontSize = 12.sp) }
                    }
                }
            }

            item {
                LxCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "现场图片（最多 3 张）",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LxInk,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            uiState.images.forEach { file ->
                                Box(
                                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)),
                                ) {
                                    AsyncImage(
                                        model = file,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().background(LxCream),
                                    )
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "移除",
                                        tint = LxInk,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(18.dp)
                                            .clickable { viewModel.removeImage(file) },
                                    )
                                }
                            }
                            if (uiState.images.size < 3) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(LxCream)
                                        .border(1.dp, LxSandDeep, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.HomeRepairService,
                                        contentDescription = null,
                                        tint = LxInkMuted,
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LxOutlinedButton(
                                text = "从相册选择",
                                onClick = {
                                    if (uiState.images.size < 3) {
                                        albumLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.images.size < 3,
                            )
                            LxOutlinedButton(
                                text = "拍照",
                                onClick = {
                                    if (uiState.images.size < 3) {
                                        val dir = File(context.cacheDir, "images").apply { mkdirs() }
                                        val file = File(dir, "repair_${System.currentTimeMillis()}.jpg")
                                        pendingCameraFile = file
                                        pendingCameraUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file,
                                        )
                                        cameraPermission.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.images.size < 3,
                            )
                        }
                    }
                }
            }

            item {
                LxButton(
                    text = if (uiState.isSubmitting) "提交中…" else "提交报修",
                    onClick = viewModel::submit,
                    enabled = !uiState.isSubmitting,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun RepairDropdown(
    label: String,
    value: String,
    nodes: List<RepairNode>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(MaterialTheme.shapes.small)
                .background(LxCream)
                .border(1.dp, LxSandDeep, MaterialTheme.shapes.small)
                .clickable(enabled = nodes.isNotEmpty()) { expanded = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = LxInkMuted,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = value.ifBlank { if (nodes.isEmpty()) "暂无可选项" else "请选择" },
                fontSize = 14.sp,
                color = if (value.isBlank()) LxInkMuted else LxInk,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = LxInkMuted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            nodes.forEach { node ->
                DropdownMenuItem(
                    text = { Text(node.name) },
                    onClick = {
                        expanded = false
                        onSelect(node.value)
                    },
                )
            }
        }
    }
}
