package com.lightxin.feature.aiclass.ui

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.lightxin.core.designsystem.component.LxProgressIndicator
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.feature.aiclass.domain.AiClassQrPayload
import java.util.concurrent.Executors

private const val SCAN_LOG_TAG = "AiClassScan"
private const val OFFICIAL_AUTO_ZOOM_MAX_RATIO = 4f

@Composable
fun AiClassScanScreen(
    onBack: () -> Unit,
    onScanResult: (AiClassQrPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    var permissionGranted by remember { mutableStateOf(false) }
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Scanning) }

    // 权限请求
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    if (!permissionGranted) {
        CameraPermissionRequest(
            onGranted = { permissionGranted = true },
            onBack = onBack,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            LxTopBar(
                title = "扫码签到",
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 相机预览
            if (scanState is ScanState.Scanning) {
                CameraPreview(
                    onQrCodeDetected = { rawValue ->
                        if (scanState is ScanState.Scanning) {
                            Log.i(
                                SCAN_LOG_TAG,
                                "QR raw detected, preview=${rawValue.previewForLog()}",
                            )
                            val payload = extractQrPayload(rawValue)
                            if (payload != null) {
                                Log.i(
                                    SCAN_LOG_TAG,
                                    "Token extracted, preview=${payload.token.previewForLog()}, length=${payload.token.length}",
                                )
                                scanState = ScanState.Success
                                onScanResult(payload)
                            } else {
                                Log.w(
                                    SCAN_LOG_TAG,
                                    "QR detected but token extraction failed, preview=${rawValue.previewForLog()}",
                                )
                            }
                        }
                    },
                )
            }

            // 扫描提示
            AnimatedVisibility(
                visible = scanState is ScanState.Scanning,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                ScanOverlay()
            }

            AnimatedVisibility(
                visible = scanState is ScanState.Scanning,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Text(
                    text = "将二维码对准框内",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            // 成功状态
            if (scanState is ScanState.Success) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "扫码成功，正在签到...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LxProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onQrCodeDetected: (String) -> Unit,
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanner: BarcodeScanner? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner?.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    val maxSupportedZoomRatio = minOf(
                        camera.cameraInfo.zoomState.value?.maxZoomRatio ?: OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                        OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                    )
                    val zoomCallback = ZoomSuggestionOptions.ZoomCallback { suggestedZoomRatio ->
                        applyOfficialZoomSuggestion(
                            camera = camera,
                            suggestedZoomRatio = suggestedZoomRatio,
                            maxSupportedZoomRatio = maxSupportedZoomRatio,
                        )
                    }
                    scanner?.close()
                    scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .setZoomSuggestionOptions(
                                ZoomSuggestionOptions.Builder(zoomCallback)
                                    .setMaxSupportedZoomRatio(maxSupportedZoomRatio)
                                    .build(),
                            )
                            .build(),
                    )
                    analysis.setAnalyzer(executor) { imageProxy ->
                        processImage(
                            imageProxy = imageProxy,
                            scanner = scanner,
                            onResult = onQrCodeDetected,
                        )
                    }
                } catch (e: Exception) {
                    Log.e(SCAN_LOG_TAG, "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ScanOverlay(
    frameSize: Dp = 240.dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(frameSize)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.large,
                ),
        )

        Text(
            text = "二维码放入框内自动识别",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner?,
    onResult: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || scanner == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.firstNotNullOfOrNull { barcode ->
                barcode.rawValue?.trim()?.takeIf { it.isNotBlank() }
            }?.let {
                Log.i(SCAN_LOG_TAG, "MLKit recognized QR content, preview=${it.previewForLog()}")
                onResult(it)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun applyOfficialZoomSuggestion(
    camera: Camera,
    suggestedZoomRatio: Float,
    maxSupportedZoomRatio: Float,
): Boolean {
    val currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
    val targetZoom = suggestedZoomRatio.coerceIn(1f, maxSupportedZoomRatio)
    if (targetZoom <= currentZoom + 0.01f) {
        return false
    }
    camera.cameraControl.setZoomRatio(targetZoom)
    Log.i(
        SCAN_LOG_TAG,
        "Official zoom suggestion applied, zoom=$currentZoom->$targetZoom",
    )
    return true
}

/**
 * 从二维码内容提取原始内容与 token。
 * 二维码可能是完整 URL（含 token 参数）或直接是 token 字符串。
 */
private fun extractQrPayload(rawValue: String): AiClassQrPayload? {
    val trimmed = rawValue.trim()

    runCatching { Uri.parse(trimmed) }.getOrNull()?.let { uri ->
        uri.getQueryParameter("token")
            ?.takeIf { it.isNotBlank() }
            ?.let { return AiClassQrPayload(rawValue = trimmed, token = it) }
    }

    if (trimmed.contains("token=")) {
        val token = Regex("[?&]token=([^&#]+)").find(trimmed)?.groupValues?.getOrNull(1)
        if (!token.isNullOrBlank()) {
            return AiClassQrPayload(rawValue = trimmed, token = token)
        }
    }

    if (trimmed.matches(Regex("^[A-Za-z0-9_-]{20,}$"))) {
        return AiClassQrPayload(rawValue = trimmed, token = trimmed)
    }

    return null
}

private fun String.previewForLog(maxLen: Int = 48): String {
    if (isBlank()) return "<blank>"
    return if (length <= maxLen) this else take(maxLen) + "..."
}

@Composable
private fun CameraPermissionRequest(
    onGranted: () -> Unit,
    onBack: () -> Unit,
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onGranted() else onBack()
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = { LxTopBar(title = "扫码签到", onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "需要相机权限才能扫码签到",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface ScanState {
    data object Scanning : ScanState
    data object Success : ScanState
}
