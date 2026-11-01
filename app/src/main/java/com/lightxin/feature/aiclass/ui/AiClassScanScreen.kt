package com.lightxin.feature.aiclass.ui

import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightxin.core.designsystem.component.LxProgressIndicator
import com.lightxin.core.designsystem.component.LxTopBar
import com.lightxin.core.designsystem.component.QrCodeScanner
import com.lightxin.core.designsystem.component.previewForLog
import com.lightxin.feature.aiclass.domain.AiClassQrPayload

private const val SCAN_LOG_TAG = "AiClassScan"

@Composable
fun AiClassScanScreen(
    onBack: () -> Unit,
    onScanResult: (AiClassQrPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Scanning) }

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
            // 相机预览 + 取景框（权限请求由 QrCodeScanner 内部处理）
            if (scanState is ScanState.Scanning) {
                QrCodeScanner(
                    onDetected = { rawValue ->
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

private sealed interface ScanState {
    data object Scanning : ScanState
    data object Success : ScanState
}
