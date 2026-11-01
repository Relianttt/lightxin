package com.lightxin.core.designsystem.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val SCAN_LOG_TAG = "QrCodeScanner"
private const val OFFICIAL_AUTO_ZOOM_MAX_RATIO = 4f

/**
 * 通用二维码扫描：相机权限、预览、取景框遮罩都由它负责。
 * 业务方只拿原始文本，识别失败/成功后怎么处理由调用方决定。
 */
@Composable
fun QrCodeScanner(
    onDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission(context)) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "需要相机权限才能扫码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreview(onQrCodeDetected = onDetected)
        ScanOverlay()
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraPreview(
    onQrCodeDetected: (String) -> Unit,
    onCameraReady: (Camera) -> Unit = {},
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
            var camera: Camera? = null

            val scaleDetector = ScaleGestureDetector(
                ctx,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val cam = camera ?: return false
                        val zoomState = cam.cameraInfo.zoomState.value ?: return false
                        val maxZoom = minOf(
                            zoomState.maxZoomRatio ?: OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                            OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                        )
                        val currentZoom = zoomState.zoomRatio ?: 1f
                        val newZoom = (currentZoom * detector.scaleFactor)
                            .coerceIn(1f, maxZoom)
                        cam.cameraControl.setZoomRatio(newZoom)
                        return true
                    }
                },
            )
            previewView.setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    camera = boundCamera
                    onCameraReady(boundCamera)
                    val maxSupportedZoomRatio = minOf(
                        boundCamera.cameraInfo.zoomState.value?.maxZoomRatio ?: OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                        OFFICIAL_AUTO_ZOOM_MAX_RATIO,
                    )
                    val zoomCallback = ZoomSuggestionOptions.ZoomCallback { suggestedZoomRatio ->
                        applyOfficialZoomSuggestion(
                            camera = boundCamera,
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

internal fun String.previewForLog(maxLen: Int = 48): String {
    if (isBlank()) return "<blank>"
    return if (length <= maxLen) this else take(maxLen) + "..."
}
