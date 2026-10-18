package com.lightxin.feature.leave.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveFormSchemaParserTest {

    @Test
    fun `data 包裹的 flowSheet fields 形态`() {
        val json = """
            {"data":{"flowSheet":{"fields":[
              {"name":"QJYY","label":"请假原因","type":"textarea","required":true},
              {"name":"SFXYLX","label":"是否离校","type":"radio","options":[{"label":"是","value":"1"},{"label":"否","value":"0"}]}
            ]}}}
        """.trimIndent()
        val fields = LeaveFormSchemaParser.parse(JsonParser.parseString(json))
        assertEquals(2, fields.size)
        assertEquals("QJYY", fields[0].key)
        assertEquals(LeaveFormType.TEXTAREA, fields[0].type)
        assertTrue(fields[0].required)
        assertEquals(LeaveFormType.RADIO, fields[1].type)
        assertEquals(2, fields[1].options.size)
        assertEquals("0", fields[1].options[1].value)
    }

    @Test
    fun `formFields 与字段别名归一`() {
        val json = """
            {"flowSheet":{"formFields":[
              {"fieldName":"F1668500437054","fieldTitle":"学院","componentType":"input"},
              {"fieldName":"QJRQ","fieldTitle":"请假日期","componentType":"dateRange"},
              {"fieldName":"QJSQ_FJ","fieldTitle":"附件","componentType":"uploadFile"}
            ]}}
        """.trimIndent()
        val fields = LeaveFormSchemaParser.parse(JsonParser.parseString(json))
        assertEquals(listOf("F1668500437054", "QJRQ", "QJSQ_FJ"), fields.map { it.key })
        assertEquals(
            listOf(LeaveFormType.TEXT_INPUT, LeaveFormType.DATE_RANGE, LeaveFormType.UPLOAD_FILE),
            fields.map { it.type },
        )
    }

    @Test
    fun `真实 formJson 的容器嵌套与 properties 选项`() {
        val json = """
            {"list":[{"type":"card","label":"基础信息","key":"basic","list":[
              {"type":"grid","key":"g1","columns":[{"span":12,"list":[
                {"type":"input","label":"标题","model":"flowTitle","key":"flowTitle","rules":[{"required":false}]}
              ]}]}
            ]},{"type":"card","label":"请假信息","key":"card2","list":[
              {"type":"radio","label":"请假类型","model":"F1","key":"F1","rules":[{"required":true}],
               "properties":{"options":[{"value":"事假","label":"事假"},{"value":"病假","label":"病假"}]}},
              {"type":"formula","label":"请假时长","model":"QJSC","key":"QJSC","properties":{"disabled":true},
               "formula":[{"type":"select","label":"学院","model":"F2","key":"F2","properties":{"options":[]}}]}
            ]}]}
        """.trimIndent()
        val fields = LeaveFormSchemaParser.parse(JsonParser.parseString(json))
        assertEquals(listOf("flowTitle", "F1", "QJSC"), fields.map { it.key })
        assertEquals(LeaveFormType.FORMULA, fields.last().type)
        assertTrue(fields.last().readonly)
        val radio = fields.first { it.key == "F1" }
        assertEquals(LeaveFormType.RADIO, radio.type)
        assertTrue(radio.required)
        assertEquals(2, radio.options.size)
        assertEquals("病假", radio.options[1].value)
    }

    @Test
    fun `裸数组输入与未知类型降级为只读`() {
        val json = """
            [{"key":"foo","label":"未知控件","type":"cascader"},
             {"key":"bar","label":"地址","type":"address"}]
        """.trimIndent()
        val fields = LeaveFormSchemaParser.parse(JsonParser.parseString(json))
        assertEquals(LeaveFormType.UNKNOWN, fields.first { it.key == "foo" }.type)
        assertEquals(LeaveFormType.ADDRESS, fields.first { it.key == "bar" }.type)
        assertEquals("foo", fields.first { it.key == "foo" }.key)
    }
}
