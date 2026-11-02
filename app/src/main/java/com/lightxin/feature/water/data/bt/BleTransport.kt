package com.lightxin.feature.water.data.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.lightxin.feature.water.data.QzxyException
import com.lightxin.feature.water.domain.WaterTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE GATT 传输。本校热水表走这条（btsnoop 实证，报告 §9.11）：服务 `FF00`，
 * 向 `FF02` 无应答写（每包 ≤20 字节），从 `FF01` 收通知；帧文本与 SPP 完全相同。
 * 只连已知 MAC，不扫描。
 */
@Singleton
class BleTransport @Inject constructor(
    @ApplicationContext private val context: Context,
) : WaterTransport {

    private val mutex = Mutex()
    private var gatt: BluetoothGatt? = null
    private var writer: BluetoothGattCharacteristic? = null
    private var assembler = FrameAssembler()

    @Volatile private var frames: Channel<Frame>? = null

    @Volatile private var connected = false

    /** 当前唯一在途的 GATT 操作；回调用状态码完成它。 */
    @Volatile private var pending: CompletableDeferred<Int>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected = true
                pending?.complete(status)
            } else {
                Log.d(TAG, "连接断开 status=$status state=$newState")
                connected = false
                pending?.complete(if (status == BluetoothGatt.GATT_SUCCESS) DISCONNECTED else status)
                frames?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            pending?.complete(status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pending?.complete(status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onNotify(value)
        }

        @Deprecated("API 33 以下的回调")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onNotify(characteristic.value ?: return)
        }
    }

    override suspend fun connect(mac: String) {
        mutex.withLock {
            if (connected && gatt != null) return
            closeLocked()
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw QzxyException("此手机不支持蓝牙")
            val device = try {
                adapter.getRemoteDevice(mac)
            } catch (e: IllegalArgumentException) {
                throw QzxyException("连接设备失败", e)
            }
            // BLE 首连偶发 133，官方 App 也会连续发起多次连接
            repeat(CONNECT_ATTEMPTS) { attempt ->
                if (open(device)) {
                    Log.d(TAG, "已连接 $mac")
                    return
                }
                Log.d(TAG, "第 ${attempt + 1} 次连接失败")
                closeLocked()
            }
            throw QzxyException("连接设备失败")
        }
    }

    override suspend fun request(cmd: Int, data: ByteArray, timeoutMs: Long): Frame = mutex.withLock {
        val gatt = gatt?.takeIf { connected } ?: throw QzxyException("设备未连接")
        val writer = writer ?: throw QzxyException("设备未连接")
        val channel = frames ?: throw QzxyException("设备未连接")
        val payload = QzxyFrame.encode(cmd, data)
        Log.d(TAG, "→ " + payload.toString(Charsets.US_ASCII).trim())

        var offset = 0
        while (offset < payload.size) {
            val chunk = payload.copyOfRange(offset, minOf(offset + CHUNK_SIZE, payload.size))
            val status = gattOp(OP_TIMEOUT_MS) { writeChunk(gatt, writer, chunk) }
            if (status != BluetoothGatt.GATT_SUCCESS) throw QzxyException("与设备断开连接")
            offset += chunk.size
        }
        channel.awaitResponse(cmd, timeoutMs).also { Log.d(TAG, "← cmd=%02x".format(it.cmd)) }
    }

    override fun close() {
        closeLocked()
    }

    @SuppressLint("MissingPermission")
    private suspend fun open(device: BluetoothDevice): Boolean {
        frames = Channel(Channel.UNLIMITED)
        assembler = FrameAssembler()

        var opened: BluetoothGatt? = null
        val state = gattOp(CONNECT_TIMEOUT_MS) {
            opened = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            opened != null
        }
        val g = opened ?: return false
        gatt = g
        if (state != BluetoothGatt.GATT_SUCCESS || !connected) return false

        if (gattOp(OP_TIMEOUT_MS) { g.discoverServices() } != BluetoothGatt.GATT_SUCCESS) return false
        val service = g.getService(SERVICE_UUID) ?: return false
        val notify = service.getCharacteristic(NOTIFY_UUID) ?: return false
        val write = service.getCharacteristic(WRITE_UUID) ?: return false
        if (!g.setCharacteristicNotification(notify, true)) return false
        val cccd = notify.getDescriptor(CCCD_UUID) ?: return false
        val enabled = gattOp(OP_TIMEOUT_MS) {
            writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
        if (enabled != BluetoothGatt.GATT_SUCCESS) return false
        writer = write
        return true
    }

    /** 发起一个 GATT 操作并等待其回调；发起失败或超时返回负数。 */
    private suspend fun gattOp(timeoutMs: Long, start: () -> Boolean): Int {
        val deferred = CompletableDeferred<Int>()
        pending = deferred
        if (!start()) {
            pending = null
            return START_FAILED
        }
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: TIMEOUT
    }

    private fun onNotify(value: ByteArray) {
        val channel = frames ?: return
        assembler.feed(value).forEach { channel.trySend(it) }
    }

    @SuppressLint("MissingPermission")
    private fun writeChunk(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        chunk: ByteArray,
    ): Boolean {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, chunk, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = type
            @Suppress("DEPRECATION")
            characteristic.value = chunk
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

    @SuppressLint("MissingPermission")
    private fun closeLocked() {
        gatt?.let { g ->
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        gatt = null
        writer = null
        connected = false
        pending?.cancel()
        pending = null
        frames?.close()
        frames = null
    }

    private companion object {
        const val TAG = "QzxyBle"
        const val CONNECT_ATTEMPTS = 2
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val OP_TIMEOUT_MS = 5_000L
        const val CHUNK_SIZE = 20
        const val DISCONNECTED = -1
        const val START_FAILED = -2
        const val TIMEOUT = -3
        val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
