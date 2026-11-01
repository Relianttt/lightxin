package com.lightxin.feature.water.data.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.lightxin.feature.water.data.QzxyException
import com.lightxin.feature.water.domain.WaterTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 经典蓝牙 SPP（RFCOMM）传输。帧协议见 [QzxyFrame]。
 *
 * 先试 insecure 连接，失败再试 secure（可能弹系统配对框）；哪种能连通属待真机验证项。
 */
@Singleton
class SppTransport @Inject constructor() : WaterTransport {

    private val mutex = Mutex()
    private var scope: CoroutineScope? = null
    private var socket: BluetoothSocket? = null
    private var frames: Channel<Frame>? = null
    private var reader: Job? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect(mac: String) {
        mutex.withLock {
            if (socket?.isConnected == true) return
            closeLocked()
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: throw QzxyException("此手机不支持蓝牙")
            adapter.cancelDiscovery()
            val device = try {
                adapter.getRemoteDevice(mac)
            } catch (e: IllegalArgumentException) {
                throw QzxyException("连接设备失败", e)
            }

            val connected = openSocket(device)
            val channel = Channel<Frame>(Channel.UNLIMITED)
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val job = newScope.launch { readLoop(connected.inputStream, channel) }
            scope = newScope
            socket = connected
            frames = channel
            reader = job
            Log.d(TAG, "已连接 $mac")
        }
    }

    override suspend fun request(cmd: Int, data: ByteArray, timeoutMs: Long): Frame = mutex.withLock {
        val socket = socket?.takeIf { it.isConnected } ?: throw QzxyException("设备未连接")
        val channel = frames ?: throw QzxyException("设备未连接")
        val payload = QzxyFrame.encode(cmd, data)
        Log.d(TAG, "→ " + payload.toString(Charsets.US_ASCII).trim())

        try {
            withContext(Dispatchers.IO) {
                socket.outputStream.write(payload)
                socket.outputStream.flush()
            }
        } catch (e: IOException) {
            throw QzxyException("与设备断开连接", e)
        }

        val matched = withTimeoutOrNull(timeoutMs) {
            var found: Frame? = null
            while (found == null) {
                val candidate = channel.receive()
                if (candidate.direction == QzxyFrame.RESPONSE && candidate.cmd == cmd) {
                    found = candidate
                } else {
                    Log.d(TAG, "丢弃无关帧 dir=${candidate.direction} cmd=${candidate.cmd}")
                }
            }
            found
        }
        matched ?: throw QzxyException("等待设备响应超时")
    }

    override fun close() {
        closeLocked()
    }

    private fun closeLocked() {
        runCatching { socket?.close() }
        reader?.cancel()
        scope?.cancel()
        frames?.close()
        socket = null
        frames = null
        reader = null
        scope = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun openSocket(device: android.bluetooth.BluetoothDevice): BluetoothSocket {
        tryConnect(device.createInsecureRfcommSocketToServiceRecord(SPP_UUID))?.let { return it }
        Log.d(TAG, "insecure 连接失败，改试 secure")
        tryConnect(device.createRfcommSocketToServiceRecord(SPP_UUID))?.let { return it }
        throw QzxyException("连接设备失败")
    }

    private suspend fun tryConnect(socket: BluetoothSocket): BluetoothSocket? {
        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching { socket.connect() }.isSuccess
            }
        }
        if (ok == true) return socket
        runCatching { socket.close() }
        return null
    }

    private fun readLoop(input: InputStream, channel: Channel<Frame>) {
        val buffer = ByteArray(1024)
        val line = StringBuilder()
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                for (i in 0 until count) {
                    val char = buffer[i].toInt().toChar()
                    when (char) {
                        '\n' -> {
                            val text = line.toString()
                            line.setLength(0)
                            Log.d(TAG, "← " + text.trim())
                            QzxyFrame.decodeLine(text)?.let { channel.trySend(it) }
                        }

                        '\r' -> Unit

                        else -> {
                            line.append(char)
                            if (line.length > MAX_LINE) line.setLength(0)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "读取结束：${e.message}")
        } finally {
            channel.close()
        }
    }

    private companion object {
        const val TAG = "Qzxy"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val MAX_LINE = 4096
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
