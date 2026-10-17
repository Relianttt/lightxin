package com.lightxin.feature.running.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「轨迹值明文、其余键值全加密」这条约束的机械门禁。
 *
 * 门禁只覆盖加密边界与明文形态：这是唯一允许 `runningRoute` 值不加密的地方，
 * 有人把它改回 `RSAUtils.encryptSportData(value)` 或在加字段时漏掉，这里立刻失败。
 */
class RunningEncryptionTest {

    private val routeJson = """[{"latitude":"31.138930","longitude":"118.628131"}]"""

    private val payload = RunningUploadPayload(
        exerciseId = "E1",
        memberId = "M1",
        runningType = "1",
        startDate = "2026-10-17 18:00:00",
        endDate = "2026-10-17 18:24:00",
        mileKm = 3.2,
        timeSeconds = 1440,
        speedKmh = 8.0,
        runningRoute = emptyList(),
        sourceInfo = "Pixel 7",
    )

    @Test
    fun `plain fields keep upload order and decimal format`() {
        val fields = plainUploadFields(payload, routeJson)

        assertEquals(
            listOf(
                "exerciseId", "memberId", "runningType", "startDate", "endDate",
                "mile", "time", "speed", "runningRoute", "sourceInfo",
            ),
            fields.keys.toList(),
        )
        assertEquals("3.20", fields["mile"])
        assertEquals("8.00", fields["speed"])
        assertEquals("1440", fields["time"])
        assertEquals(routeJson, fields["runningRoute"])
    }

    @Test
    fun `route value stays plaintext while every key and other value is encrypted`() {
        val fields = plainUploadFields(payload, routeJson)

        val encrypted = encryptUploadFields(fields) { "enc($it)" }

        assertEquals(fields.size, encrypted.size)
        // 键全部加密
        assertTrue(encrypted.keys.all { it.startsWith("enc(") })
        // 轨迹值明文，原样透传
        assertEquals(routeJson, encrypted["enc(runningRoute)"])
        assertFalse(encrypted["enc(runningRoute)"]!!.startsWith("enc("))
        // 其余值全部加密
        fields.filterKeys { it != RUNNING_ROUTE_FIELD }.forEach { (key, value) ->
            assertEquals("enc($value)", encrypted["enc($key)"])
        }
    }

    @Test
    fun `route field name is the one negotiated with the server`() {
        // 单数，不是 H5 那条 addExtractCheckRecord.do 的复数 runningRoutes
        assertEquals("runningRoute", RUNNING_ROUTE_FIELD)
    }
}
