package com.lightxin.feature.checkin.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.lightxin.core.designsystem.component.LxBottomSheet
import com.lightxin.core.designsystem.component.LxButton
import com.lightxin.core.designsystem.component.LxCard
import com.lightxin.core.designsystem.component.LxDetailRow
import com.lightxin.core.designsystem.component.LxError
import com.lightxin.core.designsystem.component.LxLoading
import com.lightxin.core.designsystem.component.LxMetaRow
import com.lightxin.core.designsystem.component.LxOutlinedButton
import com.lightxin.core.designsystem.component.LxPrimaryButton
import com.lightxin.core.designsystem.component.LxTextAction
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.theme.LxAccent
import com.lightxin.core.designsystem.theme.LxDanger
import com.lightxin.core.designsystem.theme.LxDangerSurface
import com.lightxin.core.designsystem.theme.LxStateSuccess
import com.lightxin.core.designsystem.theme.LxSuccess
import com.lightxin.core.designsystem.theme.LxText
import com.lightxin.core.designsystem.theme.LxTextMuted
import com.lightxin.feature.checkin.domain.TaskDetail
import java.io.File
import java.time.LocalTime

@Composable
fun CheckinDetailScreen(
    onBack: () -> Unit,
    onSubmitSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckinDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val error = uiState.error
    var photoFile by remember { mutableStateOf<File?>(null) }
    val finish = {
        onSubmitSuccess()
        onBack()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LxTopBar(title = "签到", onBack = onBack) },
    ) { padding ->
        when {
            uiState.isLoading -> LxLoading(modifier = Modifier.padding(padding))
            error != null -> LxError(
                message = error,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )
            else -> uiState.detail?.let { detail ->
                DetailContent(
                    detail = detail,
                    uiState = uiState,
                    photoFile = photoFile,
                    onPhotoFileChange = { photoFile = it },
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (uiState.submitSuccess) {
        SuccessSheet(
            signedAt = uiState.signedAt,
            detail = uiState.detail,
            onFinish = finish,
            onDismiss = finish,
        )
    }

    uiState.submitError?.let { message ->
        FailureSheet(
            message = message,
            onRetry = {
                viewModel.clearSubmitError()
                viewModel.submitSignIn(photoFile)
            },
            onBack = viewModel::clearSubmitError,
            onDismiss = viewModel::clearSubmitError,
        )
    }
}

@Composable
private fun SuccessSheet(
    signedAt: LocalTime?,
    detail: TaskDetail?,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    LxBottomSheet(onDismiss = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(LxStateSuccess.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = LxStateSuccess, modifier = Modifier.size(26.dp))
            }
            Text(
                text = "签到成功",
                fontSize = 18.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                modifier = Modifier.padding(top = 14.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            signedAt?.let { time ->
                LxMetaRow(label = "时间", value = "%02d:%02d".format(time.hour, time.minute))
            }
            detail?.let {
                val place = it.address.ifBlank { it.signinPlace }
                if (place.isNotBlank()) LxMetaRow(label = "地点", value = place)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            LxPrimaryButton(text = "完成", onClick = onFinish)
        }
    }
}

@Composable
private fun FailureSheet(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
    }
    LxBottomSheet(onDismiss = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(LxDangerSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = LxDanger, modifier = Modifier.size(26.dp))
            }
            Text(
                text = "签到没有成功",
                fontSize = 18.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = LxText,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = message,
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
                color = LxTextMuted,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.padding(top = 10.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            LxPrimaryButton(text = "重试", onClick = onRetry)
            LxTextAction(
                text = "返回修改",
                onClick = onBack,
                color = LxTextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}


@Composable
private fun DetailContent(
    detail: TaskDetail,
    uiState: CheckinDetailUiState,
    photoFile: File?,
    onPhotoFileChange: (File) -> Unit,
    viewModel: CheckinDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 相机拍照文件
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && tempPhotoUri != null) {
            viewModel.onPhotoTaken(tempPhotoUri)
        }
    }

    // 相机权限
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            tempPhotoUri?.let { uri -> cameraLauncher.launch(uri) }
        }
    }

    // 定位权限
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            viewModel.requestLocation()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // 任务信息
        TaskInfoCard(detail = detail)

        Spacer(modifier = Modifier.height(16.dp))

        // 已签到提示
        if (detail.isSigned) {
            LxCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = LxSuccess,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已完成签到",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LxSuccess,
                    )
                }
            }
            return
        }

        // 拍照区域
        if (detail.needPhoto) {
            PhotoSection(
                photoUri = uiState.photoUri,
                onTakePhoto = {
                    val file = File(context.cacheDir, "images").also { it.mkdirs() }
                        .let { File(it, "checkin_${System.currentTimeMillis()}.jpg") }
                    onPhotoFileChange(file)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    tempPhotoUri = uri
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 定位区域
        LocationSection(
            uiState = uiState,
            onRequestLocation = {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 签到按钮
        val canSubmit = uiState.locationStatus == LocationStatus.SUCCESS
                && (!detail.needPhoto || uiState.photoUri != null)
                && !uiState.isSubmitting

        LxButton(
            text = if (uiState.isSubmitting) "签到中..." else "一键签到",
            onClick = { viewModel.submitSignIn(photoFile) },
            enabled = canSubmit,
        )
    }
}

@Composable
private fun TaskInfoCard(detail: TaskDetail) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = detail.taskName.ifBlank { "查寝签到" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            LxDetailRow(label = "签到时间", value = "${detail.startTime} ~ ${detail.endTime}")
            if (detail.address.isNotBlank()) {
                LxDetailRow(label = "签到地点", value = detail.address)
            }
            if (detail.locationRange > 0) {
                LxDetailRow(label = "签到范围", value = "${detail.locationRange.toInt()}米")
            }
            LxDetailRow(
                label = "需要拍照",
                value = if (detail.needPhoto) "是" else "否",
                showDivider = false,
            )
        }
    }
}

@Composable
private fun PhotoSection(
    photoUri: Uri?,
    onTakePhoto: () -> Unit,
) {
    LxCard {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "签到照片",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "签到照片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.height(12.dp))
                LxOutlinedButton(text = "重新拍照", onClick = onTakePhoto)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击拍照",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LxOutlinedButton(text = "拍照", onClick = onTakePhoto)
            }
        }
    }
}

@Composable
private fun LocationSection(
    uiState: CheckinDetailUiState,
    onRequestLocation: () -> Unit,
) {
    LxCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "当前定位",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                when (uiState.locationStatus) {
                    LocationStatus.SUCCESS -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = LxSuccess,
                        modifier = Modifier.size(20.dp),
                    )
                    LocationStatus.LOCATING -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.locationStatus) {
                LocationStatus.SUCCESS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%.6f, %.6f".format(uiState.bdLng, uiState.bdLat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LxOutlinedButton(text = "重新定位", onClick = onRequestLocation)
                }

                LocationStatus.FAILED -> {
                    Text(
                        text = "定位失败，请检查权限和GPS开关",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LxOutlinedButton(text = "重试定位", onClick = onRequestLocation)
                }

                LocationStatus.LOCATING -> {
                    Text(
                        text = "正在获取位置...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LocationStatus.IDLE -> {
                    LxOutlinedButton(
                        text = "获取定位",
                        onClick = onRequestLocation,
                    )
                }
            }
        }
    }
}
