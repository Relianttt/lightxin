package com.lightxin.feature.water.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import kotlinx.coroutines.launch

/** 蓝牙用途：连接已知设备，或搜索附近设备。 */
enum class BluetoothUse { Connect, Scan }

/**
 * 蓝牙前置检查：权限 →（Android 11 及以下搜索时）系统定位 → 蓝牙开关，全部通过才执行动作。
 * 返回值接收要执行的动作。
 */
@Composable
fun rememberBluetoothGate(use: BluetoothUse, snackbarHostState: SnackbarHostState): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissions = remember(use) { requiredPermissions(use) }

    fun showMessage(message: String, settingsAction: String? = null) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(message = message, actionLabel = settingsAction?.let { "设置" })
            if (result == SnackbarResult.ActionPerformed && settingsAction != null) {
                val intent = if (settingsAction == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    Intent(settingsAction, "package:${context.packageName}".toUri())
                } else {
                    Intent(settingsAction)
                }
                context.startActivity(intent)
            }
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingAction?.invoke()
        pendingAction = null
    }

    fun proceed(action: () -> Unit) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        when {
            adapter == null -> showMessage("此手机不支持蓝牙")
            use == BluetoothUse.Scan && Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                !isLocationEnabled(context) ->
                showMessage("搜索附近设备需要先打开定位", Settings.ACTION_LOCATION_SOURCE_SETTINGS)

            !isBluetoothEnabled(adapter) -> {
                pendingAction = action
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }

            else -> action()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.values.all { it }) {
            action?.let(::proceed)
        } else {
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "“附近的设备”" else "定位"
            showMessage("需要${name}权限", Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    return { action ->
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            proceed(action)
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

private fun requiredPermissions(use: BluetoothUse): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && use == BluetoothUse.Scan ->
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(Manifest.permission.BLUETOOTH_CONNECT)
    // Android 11 及以下：连接只需安装时权限；BLE 扫描与经典查询要定位权限
    use == BluetoothUse.Scan -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    else -> emptyList()
}

private fun isLocationEnabled(context: Context): Boolean =
    context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) == true

@SuppressLint("MissingPermission")
private fun isBluetoothEnabled(adapter: BluetoothAdapter): Boolean = adapter.isEnabled
