package com.lightxin.feature.water.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lightxin.core.designsystem.component.LxNavBar
import com.lightxin.core.designsystem.component.QrCodeScanner
import com.lightxin.core.designsystem.theme.LxCanvas
import com.lightxin.feature.water.domain.parseDeviceCode
import kotlinx.coroutines.launch

private const val DEDUPE_WINDOW_MS = 1_500L
private const val TAG = "Qzxy"

@Composable
fun WaterScanScreen(
    onBack: () -> Unit,
    onOpenDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var handled by remember { mutableStateOf(false) }
    var lastRaw by remember { mutableStateOf("") }
    var lastAt by remember { mutableLongStateOf(0L) }

    Scaffold(
        modifier = modifier,
        containerColor = LxCanvas,
        topBar = { LxNavBar(onBack = onBack, title = "扫码", collapsed = true) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            QrCodeScanner(
                onDetected = { raw ->
                    if (!handled) {
                        val mac = parseDeviceCode(raw)
                        if (mac != null) {
                            handled = true
                            onOpenDevice(mac)
                        } else {
                            Log.i(TAG, raw)
                            val now = System.currentTimeMillis()
                            if (raw != lastRaw || now - lastAt > DEDUPE_WINDOW_MS) {
                                lastRaw = raw
                                lastAt = now
                                scope.launch { snackbarHostState.showSnackbar("无法识别设备码") }
                            }
                        }
                    }
                },
            )
        }
    }
}
