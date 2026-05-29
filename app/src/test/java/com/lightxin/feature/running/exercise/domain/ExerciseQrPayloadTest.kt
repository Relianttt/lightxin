package com.lightxin.feature.running.exercise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseQrPayloadTest {

    @Test
    fun `json contains all fields in original app order`() {
        val json = ExerciseQrPayload(
            exerciseId = "AUTO1",
            memberId = "M1",
            studentCode = "3243044204",
            timestamp = 1780066013621L,
        ).toJson()

        assertEquals(
            """{"exerciseId":"AUTO1","memberId":"M1","checkType":"1",""" +
                """"qrCodeType":1,"studentCode":"3243044204","timestamp":1780066013621}""",
            json,
        )
    }

    @Test
    fun `timestamp is reusable across qr content and poll param`() {
        val ts = 1780033560708L
        val payload = ExerciseQrPayload("A", "M", "3243044204", ts)

        // 二维码内容与轮询参数必须共用同一 timestamp
        assertTrue(payload.toJson().contains("\"timestamp\":$ts"))
        assertEquals(ts, payload.timestamp)
    }
}
