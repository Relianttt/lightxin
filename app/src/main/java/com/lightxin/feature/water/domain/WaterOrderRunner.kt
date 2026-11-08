package com.lightxin.feature.water.domain

import com.lightxin.feature.water.data.QzxyException
import com.lightxin.feature.water.data.bt.Frame
import com.lightxin.feature.water.data.bt.QzxyFrame
import com.lightxin.feature.water.data.bt.hexToBytes
import com.lightxin.feature.water.data.bt.toHexLower

/** 蓝牙传输层。mac 带冒号大写。 */
interface WaterTransport {
    suspend fun connect(mac: String)

    suspend fun request(cmd: Int, data: ByteArray, timeoutMs: Long = 5_000): Frame

    fun close()
}

/** 订单所依赖的服务端能力；实现见 `QzxyRepository`，单测用假实现。 */
interface WaterOrderApi {
    /** 本人账号尾号（accountId & 0xFFFF），用于判断设备是否被自己占用。 */
    suspend fun localAccountId(): Int?

    suspend fun rateOrder(device: WaterDevice, status: DeviceStatus): RateOrderInfo

    suspend fun uploadData(status: DeviceStatus, xfDataLower: String): SettleResult

    suspend fun uploadFail(consumeDate: String)
}

/** 订单状态落盘；实现见 `WaterLocalStore`。 */
interface WaterOrderStore {
    suspend fun setActiveOrder(order: ActiveOrder)

    suspend fun clearActiveOrder()

    suspend fun addRecentDevice(device: RecentDevice)
}

sealed interface StartOutcome {
    data object Running : StartOutcome

    /** 设备上有本人的未结算单，UI 引导先结算。 */
    data object NeedSettle : StartOutcome
}

/**
 * 开阀与结算编排。
 *
 * 不变量（见设计 §5.2）：没有成功的 `0x23` 绝不调 `rateOrder`；`rateOrder` 成功后到
 * `upload/data` 或 `upload/fail` 成功前 `ActiveOrder` 一直存在；上传失败保留 `ActiveOrder`
 * 供重试，重试时重走整个结算流程。
 *
 * 传输层按品类选：本校热水表走 BLE GATT，饮水机走经典蓝牙 SPP（两者均有 btsnoop 实证），帧协议相同。
 */
class WaterOrderRunner(
    private val spp: WaterTransport,
    private val ble: WaterTransport,
    private val api: WaterOrderApi,
    private val store: WaterOrderStore,
) {

    /** 本次 start/settle 最近一次 `0x23` 原始载荷（含解析失败的），失败时供诊断输出。 */
    @Volatile
    var lastStatusPayload: ByteArray? = null
        private set

    private fun transportOf(device: WaterDevice): WaterTransport = if (device.isHotWater) ble else spp

    /** [onConnected] 在蓝牙连上后立即回调，供 UI 从「连接中」切到「开阀中」。 */
    suspend fun start(
        device: WaterDevice,
        onConnected: () -> Unit = {},
    ): Result<StartOutcome> {
        val transport = transportOf(device)
        lastStatusPayload = null
        var keepOpen = false
        return try {
            transport.connect(device.macWithColon)
            onConnected()
            val status = statusOf(transport.request(QzxyFrame.CMD_STATUS, byteArrayOf(0)))

            if (status.isIdle) {
                val rate = api.rateOrder(device, status)
                val order = ActiveOrder(
                    mac = device.macWithColon,
                    deviceId = device.deviceId,
                    deviceName = device.deviceName,
                    bigTypeId = device.bigTypeId,
                    consumeDate = rate.consumeDate,
                    startedAt = System.currentTimeMillis(),
                )
                store.setActiveOrder(order)
                if (writeRate(transport, rate.downData)) {
                    store.setActiveOrder(order.copy(opened = true))
                    keepOpen = true
                    Result.success(StartOutcome.Running)
                } else {
                    Result.failure(revoke(rate.consumeDate))
                }
            } else if (status.acct == api.localAccountId()) {
                Result.success(StartOutcome.NeedSettle)
            } else {
                Result.failure(QzxyException("设备使用中"))
            }
        } catch (e: Exception) {
            Result.failure(wrap(e))
        } finally {
            if (!keepOpen) transport.close()
        }
    }

    suspend fun settle(device: WaterDevice): Result<SettleResult> {
        val transport = transportOf(device)
        lastStatusPayload = null
        return try {
            transport.connect(device.macWithColon)
            val status = statusOf(transport.request(QzxyFrame.CMD_STATUS, byteArrayOf(0)))
            // 关阀：设备可能已自行关阀，0x22 的应答不决定结算成败
            runCatching { transport.request(QzxyFrame.CMD_CLOSE, byteArrayOf(0)) }

            val collected = transport.request(QzxyFrame.CMD_COLLECT, byteArrayOf(0)).data
            if (collected.isEmpty() || (collected[0].toInt() and 0xFF) != QzxyFrame.OK) {
                throw QzxyException("读取用量失败")
            }
            val result = api.uploadData(status, collected.toHexLower())
            // 写回失败不影响结算结果：服务端已经入账
            runCatching { transport.request(QzxyFrame.CMD_ACK, QzxyFrame.ackPayload(collected)) }

            store.clearActiveOrder()
            store.addRecentDevice(
                RecentDevice(
                    mac = device.macWithColon,
                    deviceName = device.deviceName,
                    bigTypeId = device.bigTypeId,
                    roomName = device.roomName,
                    areaName = device.areaName,
                    buildingName = device.buildingName,
                    floorName = device.floorName,
                ),
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(wrap(e))
        } finally {
            transport.close()
        }
    }

    fun close() {
        spp.close()
        ble.close()
    }

    private suspend fun writeRate(transport: WaterTransport, downData: String): Boolean {
        val frame = runCatching {
            transport.request(QzxyFrame.CMD_RATE, hexToBytes(downData.lowercase()))
        }.getOrNull() ?: return false
        return frame.data.isNotEmpty() && (frame.data[0].toInt() and 0xFF) == QzxyFrame.OK
    }

    /** 开阀失败时撤销预扣；撤销本身失败则保留 ActiveOrder 让用户稍后结算。 */
    private suspend fun revoke(consumeDate: String): QzxyException {
        val revoked = runCatching { api.uploadFail(consumeDate) }.isSuccess
        return if (revoked) {
            store.clearActiveOrder()
            QzxyException("开阀失败，已撤销")
        } else {
            QzxyException("开阀失败，请稍后在设备页结算")
        }
    }

    private fun statusOf(frame: Frame): DeviceStatus {
        val data = frame.data
        lastStatusPayload = data
        if (data.isEmpty() || (data[0].toInt() and 0xFF) != QzxyFrame.OK) {
            throw QzxyException("设备未响应")
        }
        return QzxyFrame.parseStatus(data)
    }

    private fun wrap(e: Exception): Exception = when (e) {
        is UnsupportedProtocol -> QzxyException("暂不支持此设备", e)
        is QzxyException -> e
        else -> QzxyException(e.message ?: "操作失败", e)
    }
}
