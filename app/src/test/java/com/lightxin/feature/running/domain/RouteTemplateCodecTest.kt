package com.lightxin.feature.running.domain

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lightxin.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTemplateCodecTest {

    @Test
    fun `信封格式可导出并重新解析`() {
        val template = TestFixtures.routeTemplate()
        val encoded = RouteTemplateCodec.encode(listOf(template), exportedAtMillis = 123L)

        val root = JsonParser.parseString(encoded).asJsonObject
        assertEquals(1, root.get("schemaVersion").asInt)
        assertEquals("lightxin", root.get("app").asString)
        assertEquals(123L, root.get("exportedAtMillis").asLong)

        val parsed = RouteTemplateCodec.parse(encoded)
        assertEquals(1, parsed.candidates.size)
        assertEquals(template.name, parsed.candidates.first().name)
        assertEquals(template.points.size, parsed.candidates.first().points.size)
        assertTrue(parsed.rejected.isEmpty())
    }

    @Test
    fun `裸数组也能导入`() {
        val array = JsonArray().apply {
            add(templateJson(name = "裸数组模板"))
        }
        val parsed = RouteTemplateCodec.parse(array.toString())
        assertEquals("裸数组模板", parsed.candidates.single().name)
    }

    @Test
    fun `点数和距离不过硬闸的条目被拒并带原因`() {
        val tooFew = JsonObject().apply {
            addProperty("name", "点太少")
            add("points", JsonArray().apply {
                repeat(5) { index ->
                    add(pointJson(31.0 + index * 0.0001, 118.0))
                }
            })
        }
        val parsed = RouteTemplateCodec.parse(JsonArray().apply { add(tooFew) }.toString())
        assertTrue(parsed.candidates.isEmpty())
        assertEquals(1, parsed.rejected.size)
        assertTrue(parsed.rejected.first().reason.contains("轨迹点"))
    }

    @Test
    fun `距离按点位重算而不是沿用文件值`() {
        val obj = templateJson(name = "旧距离")
        obj.addProperty("totalDistanceMeters", 999_999.0)
        val parsed = RouteTemplateCodec.parse(JsonArray().apply { add(obj) }.toString())
        val item = parsed.candidates.single()
        assertTrue(item.totalDistanceMeters in 1_000.0..3_000.0)
        assertTrue(item.totalDistanceMeters < 999_999.0)
    }

    @Test
    fun `坏 JSON 抛可读异常不崩溃`() {
        val error = runCatching { RouteTemplateCodec.parse("{ not json") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("JSON"))
    }

    @Test
    fun `缺少时长时用首尾时间戳兜底`() {
        val obj = templateJson(name = "无时长")
        val parsed = RouteTemplateCodec.parse(JsonArray().apply { add(obj) }.toString())
        val expected = (TestFixtures.validPoints().last().timestampMillis -
            TestFixtures.validPoints().first().timestampMillis) / 1000L
        assertEquals(expected, parsed.candidates.single().durationSeconds)
    }

    private fun templateJson(name: String): JsonObject = JsonObject().apply {
        addProperty("id", "file-$name")
        addProperty("name", name)
        add("points", JsonArray().apply {
            TestFixtures.validPoints().forEach { point ->
                add(pointJson(point.latitude, point.longitude, point.timestampMillis))
            }
        })
    }

    private fun pointJson(lat: Double, lng: Double, ts: Long = 0L): JsonObject = JsonObject().apply {
        addProperty("latitude", lat)
        addProperty("longitude", lng)
        addProperty("timestampMillis", ts)
    }
}
