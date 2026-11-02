package com.lightxin.feature.water.data.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.lightxin.feature.water.data.QzxyException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 搜到的设备；[mac] 带冒号大写。 */
data class NearbyDevice(val mac: String, val rssi: Int?)

/**
 * 搜索附近的趣智设备，直到收集方取消。
 *
 * 热水表只有 BLE（广播名 `KLCXKJ-Water`、服务 `FEE7`，报告 §9.11），饮水机是经典蓝牙，
 * 所以 BLE 扫描与经典查询同时进行，按广播名前缀识别。所有设备同名，具体是哪台要再查设备信息。
 */
@Singleton
class NearbyScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @SuppressLint("MissingPermission")
    fun scan(): Flow<NearbyDevice> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close(QzxyException("蓝牙未开启"))
            return@callbackFlow
        }

        val leScanner = adapter.bluetoothLeScanner
        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                if (isQzxyName(record?.deviceName) || record?.serviceUuids?.contains(SERVICE_FEE7) == true) {
                    trySend(NearbyDevice(result.device.address.uppercase(), result.rssi))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "BLE 扫描失败 $errorCode")
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = IntentCompat.getParcelableExtra(
                    intent,
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java,
                ) ?: return
                if (!isQzxyName(intent.getStringExtra(BluetoothDevice.EXTRA_NAME))) return
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    .takeIf { it != Short.MIN_VALUE }?.toInt()
                trySend(NearbyDevice(device.address.uppercase(), rssi))
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            },
            // 发送方是蓝牙进程（非 system uid），NOT_EXPORTED 会收不到；这两个是受保护广播，第三方无法伪造
            ContextCompat.RECEIVER_EXPORTED,
        )
        leScanner?.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            leCallback,
        )
        adapter.startDiscovery()

        awaitClose {
            runCatching { leScanner?.stopScan(leCallback) }
            runCatching { adapter.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun isQzxyName(name: String?): Boolean = name?.startsWith(NAME_PREFIX) == true

    private companion object {
        const val TAG = "QzxyScan"
        const val NAME_PREFIX = "KLCXKJ"
        val SERVICE_FEE7: ParcelUuid = ParcelUuid.fromString("0000fee7-0000-1000-8000-00805f9b34fb")
    }
}
