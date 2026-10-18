package com.lightxin.feature.leave.domain

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveFormBuilderTest {

    private val identity = LeaveIdentity(
        userCode = "3233032235",
        userName = "何宇翔",
        userPhone = "18356220503",
        yxdm = "07",
        yxmc = "电气与电子工程学院",
        bjdm = "2330310202",
    )

    @Test
    fun `日期区间附件与时长一起装配`() {
        val defaults = JsonParser.parseString(
            """{"flowTitle":"学生请假申请-何宇翔","userName":"何宇翔","F1677131069644":"病假"}""",
        ).asJsonObject
        val fields = listOf(
            LeaveFormField("QJYY", "请假原因", LeaveFormType.TEXTAREA, required = true),
            LeaveFormField("QJSQ_FJ", "附件", LeaveFormType.UPLOAD_FILE),
            LeaveFormField("QJSC", "时长", LeaveFormType.FORMULA),
        )
        val input = LeaveFormInput(
            values = mapOf("QJYY" to "身体不舒服，回宿舍休息"),
            dateRanges = mapOf("QJRQ" to ("2026-06-01 10:00" to "2026-06-01 12:00")),
            attachments = mapOf(
                "QJSQ_FJ" to listOf(LeaveAttachment("u1", "病历.jpg", "https://file/1.jpg")),
            ),
        )

        val result = LeaveFormBuilder.build(defaults, fields, input, identity)

        assertEquals("身体不舒服，回宿舍休息", result.get("QJYY").asString)
        assertEquals(2, result.getAsJsonArray("QJRQ").size())
        assertEquals(2, result.get("QJSC").asInt)
        val attachment = result.getAsJsonArray("QJSQ_FJ")[0].asJsonObject
        assertEquals("done", attachment.get("status").asString)
        assertEquals("https://file/1.jpg", attachment.get("fileUrl").asString)
        assertEquals("https://file/1.jpg", attachment.get("url").asString)
        assertEquals("病假", result.get("F1677131069644").asString)
        assertEquals("07", result.get("yxdm").asString)
        assertEquals("3233032235", result.get("userCode").asString)
    }

    @Test
    fun `小数时长保留一位`() {
        val obj = com.google.gson.JsonObject().apply {
            add("QJRQ", com.google.gson.JsonArray().apply {
                add("2026-06-01 10:00")
                add("2026-06-01 11:30")
            })
        }
        LeaveFormBuilder.computeLeaveHours(obj)
        assertEquals(1.5, obj.get("QJSC").asDouble, 0.001)
    }

    @Test
    fun `无附件时提交空字符串且不丢未知字段`() {
        val defaults = JsonParser.parseString("""{"F1677490339658":"","QJSQ_FJ":""}""").asJsonObject
        val fields = listOf(LeaveFormField("QJSQ_FJ", "附件", LeaveFormType.UPLOAD_FILE))
        val result = LeaveFormBuilder.build(defaults, fields, LeaveFormInput(), identity)

        assertTrue(result.get("QJSQ_FJ").isJsonPrimitive)
        assertEquals("", result.get("QJSQ_FJ").asString)
        assertEquals("", result.get("F1677490339658").asString)
    }

    @Test
    fun `必填和原因长度校验`() {
        val fields = listOf(
            LeaveFormField("QJYY", "请假原因", LeaveFormType.TEXTAREA, required = true),
            LeaveFormField("QJRQ", "请假日期", LeaveFormType.DATE_RANGE, required = true),
        )
        val errors = LeaveFormValidator.validate(
            fields,
            LeaveFormInput(
                values = mapOf("QJYY" to "太短"),
                dateRanges = mapOf("QJRQ" to ("2026-06-01 12:00" to "2026-06-01 10:00")),
            ),
        )
        assertTrue(errors.any { it.key == "QJYY" && it.message.contains("10") })
        assertTrue(errors.any { it.key == "QJRQ" && it.message.contains("晚于") })
    }
}
