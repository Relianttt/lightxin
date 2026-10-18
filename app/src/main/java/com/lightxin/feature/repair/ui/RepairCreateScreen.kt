package com.lightxin.feature.repair.ui

import android.Manifest
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.lightxin.core.designsystem.component.LxErrorState
import com.lightxin.core.designsystem.component.LxChoiceGroup
import com.lightxin.core.designsystem.component.LxField
import com.lightxin.core.designsystem.component.LxGroupCard
import com.lightxin.core.designsystem.component.LxGroupDivider
import com.lightxin.core.designsystem.component.LxGroupRow
import com.lightxin.core.designsystem.component.LxInput
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.LxOptionSheet
import com.lightxin.core.designsystem.component.LxPageHeader
import com.lightxin.core.designsystem.component.LxPrimaryButton
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
import com.lightxin.feature.repair.domain.RepairNode
import kotlinx.coroutines.launch
import java.io.File

private enum class AddressPicker { Building, Room }

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
    var addressPicker by remember { mutableStateOf<AddressPicker?>(null) }
    val listState = rememberLazyListState()
    val collapsed = listState.lxHeaderCollapsed()

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
            runCatching { cameraLauncher.launch(uri) }.onFailure {
                scope.launch { snackbarHostState.showSnackbar("没有可用的相机应用，请从相册选择") }
            }
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
    // 当前只有一个校区：自动选中，界面不再暴露“校区”这一层。
    LaunchedEffect(uiState.campusNodes) {
        if (uiState.xqdm.isBlank() && uiState.campusNodes.size == 1) {
            viewModel.selectCampus(uiState.campusNodes.first().value)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = { LxNavBar(onBack = onBack, title = "新建报修", collapsed = collapsed) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LxSkeletonList(count = 5, modifier = Modifier.padding(padding))
            uiState.loadError != null -> Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LxErrorState(message = uiState.loadError.orEmpty(), onRetry = viewModel::load)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 2.dp),
            ) {
                item {
                    LxPageHeader(title = "新建报修", subtitle = "后勤受理后会电话联系你")
                    Spacer(Modifier.height(4.dp))
                }

                item {
                    LxField(label = "报修地点", required = true) {
                        LxGroupCard {
                            LxGroupRow(
                                label = "楼栋",
                                value = uiState.buildingName,
                                placeholder = "请选择",
                                enabled = uiState.buildingNodes.isNotEmpty(),
                                onClick = { addressPicker = AddressPicker.Building },
                            )
                            LxGroupDivider()
                            LxGroupRow(
                                label = "房间",
                                value = uiState.roomName,
                                placeholder = "请选择",
                                enabled = uiState.roomNodes.isNotEmpty(),
                                onClick = { addressPicker = AddressPicker.Room },
                            )
                        }
                        uiState.errors["location"]?.let {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                color = LxDanger,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    LxField(label = "详细地址", required = true, error = uiState.errors["xxdz"]) {
                        LxInput(
                            value = uiState.xxdz,
                            onValueChange = viewModel::setXxdz,
                            placeholder = "如 A36M214",
                        )
                    }
                }

                item {
                    LxField(
                        label = "报修类型",
                        required = true,
                        error = uiState.errors["bxlx"],
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LxChoiceGroup(
                                options = uiState.typeRoots.map { it.name to it.value },
                                selectedValue = uiState.typeRoot,
                                onSelect = viewModel::selectTypeRoot,
                            )
                            if (uiState.typeChildren.isNotEmpty()) {
                                LxChoiceGroup(
                                    options = uiState.typeChildren.map { it.name to it.value },
                                    selectedValue = uiState.bxlx,
                                    onSelect = viewModel::selectType,
                                )
                            }
                        }
                    }
                }

                item {
                    LxField(label = "联系电话", required = true, error = uiState.errors["sjh"]) {
                        if (uiState.xm.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = "报修人", fontSize = 13.sp, color = LxTextMuted)
                                Text(
                                    text = uiState.xm,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = LxText,
                                )
                            }
                        }
                        LxInput(
                            value = uiState.sjh,
                            onValueChange = viewModel::setPhone,
                            placeholder = "11 位手机号",
                        )
                    }
                }

                item {
                    LxField(label = "故障描述", required = true, error = uiState.errors["gzms"]) {
                        LxTextArea(
                            value = uiState.gzms,
                            onValueChange = viewModel::setGzms,
                            placeholder = "型号、现象、什么时候开始的",
                        )
                    }
                }

                item {
                    LxField(label = "现场图片") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            uiState.images.forEach { file ->
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(LxTint),
                                ) {
                                    AsyncImage(
                                        model = file,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "移除",
                                        tint = LxText,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(16.dp)
                                            .clickable { viewModel.removeImage(file) },
                                    )
                                }
                            }
                            if (uiState.images.size < 3) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(LxTint)
                                        .clickable {
                                            albumLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "添加图片",
                                        tint = LxTextMuted,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "最多 3 张，点方块从相册选择",
                                fontSize = 11.sp,
                                color = LxTextMuted,
                            )
                            LxTextAction(
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
                                        runCatching { cameraPermission.launch(Manifest.permission.CAMERA) }.onFailure {
                                            scope.launch { snackbarHostState.showSnackbar("没有可用的相机应用，请从相册选择") }
                                        }
                                    }
                                },
                                enabled = uiState.images.size < 3,
                                color = LxAccent,
                            )
                        }
                    }
                }

                item {
                    LxPrimaryButton(
                        text = if (uiState.isSubmitting) "提交中…" else "提交报修",
                        onClick = viewModel::submit,
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    val nodes: List<RepairNode> = when (addressPicker) {
        AddressPicker.Building -> uiState.buildingNodes
        AddressPicker.Room -> uiState.roomNodes
        null -> emptyList()
    }
    if (addressPicker != null) {
        LxOptionSheet(
            title = if (addressPicker == AddressPicker.Building) "选择楼栋" else "选择房间",
            options = nodes.map { it.name to it.value },
            selectedValue = if (addressPicker == AddressPicker.Building) uiState.lyId else uiState.qyfjh,
            onSelect = { _, value ->
                if (addressPicker == AddressPicker.Building) viewModel.selectBuilding(value)
                else viewModel.selectRoom(value)
            },
            onDismiss = { addressPicker = null },
        )
    }
}
