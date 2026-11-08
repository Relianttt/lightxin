package com.lightxin.feature.water

import com.lightxin.feature.water.data.QzxyDeviceData
import com.lightxin.feature.water.data.QzxyException
import com.lightxin.feature.water.data.bt.hexToBytes
import com.lightxin.feature.water.data.deviceDiagnostic
import com.lightxin.feature.water.domain.UnsupportedProtocol
import com.lightxin.feature.water.domain.WaterDevice
import com.lightxin.feature.water.ui.failureDiagnostic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterDeviceTest {

    @Test
    fun `热水与饮水不论 communicationTypeId 都支持`() {
        for (type in listOf(WaterDevice.TYPE_HOT_WATER, WaterDevice.TYPE_DRINKING)) {
            for (comm in listOf(0, 1, 2, 99)) {
                assertTrue(WaterDevice(bigTypeId = type, communicationTypeId = comm).isSupported)
            }
        }
    }

    @Test
    fun `其他大类仍不支持，isUse 不为 1 仍判停用`() {
        assertFalse(WaterDevice(bigTypeId = 0).isSupported)
        assertFalse(WaterDevice(bigTypeId = 3).isSupported)
        assertFalse(WaterDevice(bigTypeId = 4, isUse = 0).isEnabled)
    }

    @Test
    fun `诊断区分未下发与 false`() {
        val missing = deviceDiagnostic(QzxyDeviceData(bigTypeId = 4))
        assertTrue(missing.contains("isBle=未下发"))
        assertTrue(missing.contains("communicationTypeId=未下发"))
        assertTrue(missing.contains("realMac=未下发"))

        val explicit = deviceDiagnostic(QzxyDeviceData(bigTypeId = 4, communicationTypeId = 0, isBle = false))
        assertTrue(explicit.contains("isBle=false"))
        assertTrue(explicit.contains("communicationTypeId=0"))
    }

    private val device = WaterDevice(bigTypeId = 4, diagnostic = "bigTypeId=4")

    @Test
    fun `未知布局的失败诊断带原始载荷`() {
        val payload = byteArrayOf(0x80.toByte(), 0x01, 0x02)
        val error = QzxyException("暂不支持此设备", UnsupportedProtocol("设备状态长度不支持：3"))

        val text = failureDiagnostic(device, error, payload)
        assertTrue(text.startsWith("bigTypeId=4"))
        assertTrue(text.contains("error=暂不支持此设备 <- 设备状态长度不支持：3"))
        assertTrue(text.contains("0x23 len=3 hex=800102"))
        assertFalse(text.contains("state="))
    }

    @Test
    fun `能解析的帧在后续步骤失败时也带出解析结果`() {
        val payload = hexToBytes("800000020100000a0400004bb5c47f0e3e4cfd010000000001000000")

        val text = failureDiagnostic(device, QzxyException("设备使用中"), payload)
        assertTrue(text.contains("0x23 len=28 hex=800000020100000a"))
        assertTrue(text.contains("state=1 protocolType=空 randomNumber=0000 macType=0001"))
    }

    @Test
    fun `没拿到 0x23 时只有字段与错误`() {
        val text = failureDiagnostic(device, QzxyException("连接失败"), null)
        assertTrue(text.endsWith("error=连接失败"))
    }
}
