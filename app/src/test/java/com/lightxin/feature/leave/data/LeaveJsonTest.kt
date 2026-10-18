package com.lightxin.feature.leave.data

import com.google.gson.JsonParser
import com.lightxin.feature.leave.domain.LeaveFormType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveJsonTest {

    @Test
    fun `flowSheet 字符串会先解包再解析`() {
        val root = JsonParser.parseString(
            """
            {"flag":true,"data":{"flowSheet":"{\"list\":[{\"type\":\"card\",\"key\":\"c\",\"list\":[
              {\"type\":\"textarea\",\"label\":\"请假原因\",\"model\":\"QJYY\",\"key\":\"QJYY\",\"rules\":[{\"required\":true}]}
            ]}]}"}}
            """.trimIndent(),
        ).asJsonObject
        val schema = LeaveJson.parseSchema(root)
        assertEquals(1, schema.fields.size)
        assertEquals("QJYY", schema.fields.single().key)
        assertEquals(LeaveFormType.TEXTAREA, schema.fields.single().type)
        assertTrue(schema.fields.single().required)
    }

    @Test
    fun `withdraw 的字符串 1 0 按布尔处理`() {
        val yes = JsonParser.parseString("""{"withdraw":"1"}""").asJsonObject
        val no = JsonParser.parseString("""{"withdraw":"0"}""").asJsonObject
        val bool = JsonParser.parseString("""{"withdraw":true}""").asJsonObject
        assertTrue(yes.bool("withdraw"))
        assertFalse(no.bool("withdraw"))
        assertTrue(bool.bool("withdraw"))
    }
}
