package com.lightxin.feature.water

import com.lightxin.feature.water.data.QzxyException
import com.lightxin.feature.water.data.bt.Frame
import com.lightxin.feature.water.data.bt.QzxyFrame
import com.lightxin.feature.water.data.bt.hexToBytes
import com.lightxin.feature.water.data.bt.toHexLower
import com.lightxin.feature.water.domain.ActiveOrder
import com.lightxin.feature.water.domain.DeviceStatus
import com.lightxin.feature.water.domain.RateOrderInfo
import com.lightxin.feature.water.domain.RecentDevice
import com.lightxin.feature.water.domain.SettleResult
import com.lightxin.feature.water.domain.StartOutcome
import com.lightxin.feature.water.domain.WaterDevice
import com.lightxin.feature.water.domain.WaterOrderApi
import com.lightxin.feature.water.domain.WaterOrderRunner
import com.lightxin.feature.water.domain.WaterOrderStore
import com.lightxin.feature.water.domain.WaterTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterOrderRunnerTest {

    private val device = WaterDevice(
        deviceId = 915,
        deviceName = "热水表测试",
        bigTypeId = WaterDevice.TYPE_HOT_WATER,
        smallTypeId = 57,
        macAddress = "C4:7F:0E:26:9C:B1",
        communicationTypeId = 0,
        isUse = 1,
    )

    @Test
    fun `正常开阀再结算`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 0, random = "69020000")
        transport.statusPool += statusPayload(state = 1, random = "254e0000")
        val api = FakeApi()
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)

        val started = runner.start(device)
        assertEquals(StartOutcome.Running, started.getOrThrow())
        assertEquals(1, api.rateCalls.size)
        assertEquals("69020000", api.rateCalls.single().second.randomNumber)
        assertTrue(store.activeOrder!!.opened)
        assertEquals(0, transport.closeCount)

        val rateFrame = transport.requests.single { it.first == QzxyFrame.CMD_RATE }
        assertArrayEquals(hexToBytes(DOWN_DATA.lowercase()), rateFrame.second)

        val settled = runner.settle(device)
        val result = settled.getOrThrow()
        assertEquals(110L, result.consumeMoneyLi)
        assertNull(store.activeOrder)
        assertEquals(1, store.recentDevices.size)
        assertEquals(1, transport.closeCount)

        // 上传用的是结算时那次 0x23 的 randomNumber，不是开阀那次
        assertEquals("10", api.uploadCalls.single().protocolType)
        assertEquals("254e0000", api.uploadCalls.single().randomNumber)
        assertEquals(XF_DATA.toHexLower(), api.uploadCalls.single().xfData)
        val ack = transport.requests.single { it.first == QzxyFrame.CMD_ACK }
        assertArrayEquals(QzxyFrame.ackPayload(XF_DATA), ack.second)
    }

    @Test
    fun `设备被他人使用时不调用下单`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 1, acct = 0x1111)
        val api = FakeApi()
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)

        val result = runner.start(device)
        assertEquals("设备使用中", result.exceptionOrNull()?.message)
        assertTrue(api.rateCalls.isEmpty())
        assertNull(store.activeOrder)
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun `失败后保留最近一次 0x23 载荷供诊断`() = runBlocking {
        val transport = FakeTransport()
        val busy = statusPayload(state = 1, acct = 0x1111)
        transport.statusPool += busy
        val runner = WaterOrderRunner(UnusedTransport, transport, FakeApi(), FakeStore())

        assertTrue(runner.start(device).isFailure)
        assertArrayEquals(busy, runner.lastStatusPayload)
    }

    @Test
    fun `设备上有自己的未结算单时引导结算`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 6, acct = 11749)
        val api = FakeApi()
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)

        assertEquals(StartOutcome.NeedSettle, runner.start(device).getOrThrow())
        assertTrue(api.rateCalls.isEmpty())
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun `写费率失败时撤销并清掉未结算单`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 0)
        transport.failCommands += QzxyFrame.CMD_RATE
        val api = FakeApi()
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)

        val result = runner.start(device)
        assertEquals("开阀失败，已撤销", result.exceptionOrNull()?.message)
        assertEquals(listOf(RATE_CONSUME_DATE), api.failCalls)
        assertNull(store.activeOrder)
        assertEquals(1, transport.closeCount)
    }

    @Test
    fun `撤销也失败时保留未结算单`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 0)
        transport.failCommands += QzxyFrame.CMD_RATE
        val api = FakeApi()
        api.failThrows = true
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)

        val result = runner.start(device)
        assertEquals("开阀失败，请稍后在设备页结算", result.exceptionOrNull()?.message)
        // 阀门没开：重进设备页应显示「未结算」而不是「使用中」
        assertEquals(false, store.activeOrder!!.opened)
    }

    @Test
    fun `状态帧失败时绝不调用下单`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 0).also { it[0] = 0x00 }
        val api = FakeApi()
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)

        val result = runner.start(device)
        assertEquals("设备未响应", result.exceptionOrNull()?.message)
        assertTrue(api.rateCalls.isEmpty())
        assertNull(store.activeOrder)
    }

    @Test
    fun `上传失败时保留未结算单可重试`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 0)
        val api = FakeApi()
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)
        runner.start(device).getOrThrow()

        transport.statusPool += statusPayload(state = 1, random = "254e0000")
        api.uploadThrows = true
        val failed = runner.settle(device)
        assertEquals("加密校验失败", failed.exceptionOrNull()?.message)
        assertNotNull(store.activeOrder)

        // 重试时重走整个流程，重新取 randomNumber
        transport.statusPool += statusPayload(state = 1, random = "772f0000")
        api.uploadThrows = false
        assertTrue(runner.settle(device).isSuccess)
        assertEquals(2, api.uploadCalls.size)
        assertEquals("772f0000", api.uploadCalls.last().randomNumber)
        assertNull(store.activeOrder)
    }

    @Test
    fun `写回失败不影响结算结果`() = runBlocking {
        val transport = FakeTransport()
        transport.statusPool += statusPayload(state = 0)
        val api = FakeApi()
        val store = FakeStore()
        val runner = WaterOrderRunner(UnusedTransport, transport, api, store)
        runner.start(device).getOrThrow()

        transport.statusPool += statusPayload(state = 1)
        transport.failCommands += QzxyFrame.CMD_ACK
        val result = runner.settle(device).getOrThrow()
        assertEquals(110L, result.consumeMoneyLi)
        assertNull(store.activeOrder)
    }

    private fun statusPayload(
        state: Int,
        acct: Int = 11749,
        random: String = "69020000",
        protocolType: Int = 0x10,
    ): ByteArray {
        val payload = ByteArray(48)
        payload[0] = 0x80.toByte()
        payload[11] = (acct shr 8 and 0xFF).toByte()
        payload[12] = (acct and 0xFF).toByte()
        payload[19] = protocolType.toByte()
        hexToBytes(random).copyInto(payload, 24)
        payload[28] = state.toByte()
        payload[29] = 0x00
        payload[30] = 0x39
        return payload
    }

    @Test
    fun `饮水机走 SPP 传输层`() = runBlocking {
        val spp = FakeTransport()
        spp.statusPool += statusPayload(state = 0)
        val runner = WaterOrderRunner(spp, UnusedTransport, FakeApi(), FakeStore())

        val drinking = device.copy(bigTypeId = WaterDevice.TYPE_DRINKING, smallTypeId = 56)
        assertEquals(StartOutcome.Running, runner.start(drinking).getOrThrow())
        assertTrue(spp.requests.any { it.first == QzxyFrame.CMD_RATE })
    }

    /** 本用例不该用到的传输层；一旦被调用即失败。 */
    private object UnusedTransport : WaterTransport {
        override suspend fun connect(mac: String) = throw AssertionError("用错了传输层")

        override suspend fun request(cmd: Int, data: ByteArray, timeoutMs: Long): Frame =
            throw AssertionError("用错了传输层")

        override fun close() = Unit
    }

    private class FakeTransport : WaterTransport {
        val statusPool = ArrayDeque<ByteArray>()
        val requests = mutableListOf<Pair<Int, ByteArray>>()
        val failCommands = mutableSetOf<Int>()
        var closeCount = 0

        override suspend fun connect(mac: String) = Unit

        override suspend fun request(cmd: Int, data: ByteArray, timeoutMs: Long): Frame {
            requests += cmd to data
            if (cmd in failCommands) throw QzxyException("等待设备响应超时")
            val payload = when (cmd) {
                QzxyFrame.CMD_STATUS -> statusPool.removeFirst()
                QzxyFrame.CMD_COLLECT -> XF_DATA
                else -> byteArrayOf(QzxyFrame.OK.toByte())
            }
            return Frame(QzxyFrame.RESPONSE, cmd, payload)
        }

        override fun close() {
            closeCount++
        }
    }

    private class FakeApi : WaterOrderApi {
        val rateCalls = mutableListOf<Pair<WaterDevice, DeviceStatus>>()
        val uploadCalls = mutableListOf<UploadCall>()
        val failCalls = mutableListOf<String>()
        var uploadThrows = false
        var failThrows = false

        override suspend fun localAccountId(): Int? = 11749

        override suspend fun rateOrder(device: WaterDevice, status: DeviceStatus): RateOrderInfo {
            rateCalls += device to status
            return RateOrderInfo(downData = DOWN_DATA, consumeDate = RATE_CONSUME_DATE, rateLi = 50)
        }

        override suspend fun uploadData(status: DeviceStatus, xfDataLower: String): SettleResult {
            uploadCalls += UploadCall(status.protocolType, status.randomNumber, xfDataLower)
            if (uploadThrows) throw QzxyException("加密校验失败")
            return SettleResult(consumeMoneyLi = 110, balanceAfterLi = 890, orderNo = "order-1")
        }

        override suspend fun uploadFail(consumeDate: String) {
            failCalls += consumeDate
            if (failThrows) throw QzxyException("撤销失败")
        }
    }

    private class FakeStore : WaterOrderStore {
        var activeOrder: ActiveOrder? = null
        val recentDevices = mutableListOf<RecentDevice>()

        override suspend fun setActiveOrder(order: ActiveOrder) {
            activeOrder = order
        }

        override suspend fun clearActiveOrder() {
            activeOrder = null
        }

        override suspend fun addRecentDevice(device: RecentDevice) {
            recentDevices += device
        }
    }

    private data class UploadCall(val protocolType: String, val randomNumber: String, val xfData: String)

    private companion object {
        const val RATE_CONSUME_DATE = "20261101195519"
        const val DOWN_DATA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val XF_DATA = byteArrayOf(0x80.toByte()) + ByteArray(41) { (it + 1).toByte() }
    }
}
