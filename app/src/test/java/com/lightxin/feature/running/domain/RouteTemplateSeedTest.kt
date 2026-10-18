package com.lightxin.feature.running.domain

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lightxin.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTemplateSeedTest {

    private fun seedJson(): String {
        val item = JsonObject().apply {
            addProperty("name", "Ring")
            add("points", JsonArray().apply {
                TestFixtures.validPoints().forEach { point ->
                    add(JsonObject().apply {
                        addProperty("latitude", point.latitude)
                        addProperty("longitude", point.longitude)
                        addProperty("timestampMillis", point.timestampMillis)
                    })
                }
            })
        }
        return JsonArray().apply { add(item) }.toString()
    }

    @Test
    fun `种子映射为系统模板且第一条为默认`() {
        val parsed = RouteTemplateCodec.parse(seedJson())
        val templates = RouteTemplateSeed.map(parsed, nowMillis = 888L)

        assertEquals(1, templates.size)
        val template = templates.single()
        assertEquals("系统模板", template.name)
        assertEquals(RouteTemplateSource.SYSTEM, template.source)
        assertTrue(template.isDefault)
        assertEquals(888L, template.createdAtMillis)
    }

    @Test
    fun `已有用户模板时不注入`() {
        val existing = listOf(TestFixtures.routeTemplate())
        assertFalse(RouteTemplateSeed.shouldInject(markerExists = false, current = existing))
    }

    @Test
    fun `标记文件存在时不注入（删除后不复活）`() {
        assertFalse(RouteTemplateSeed.shouldInject(markerExists = true, current = emptyList()))
        assertTrue(RouteTemplateSeed.shouldInject(markerExists = false, current = emptyList()))
    }
}
