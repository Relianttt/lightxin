package com.lightxin.feature.repair.data

import com.google.gson.JsonParser
import com.lightxin.core.network.isAuthExpired
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairAuthRejectionTest {
    // 2026-10-25: 虚构身份与无效 token 请求只读列表接口的完整响应。
    private val rejected = JsonParser.parseString(
        """{"code":"非法访问","data":null,"flag":false,"msg":"","rows":null,"total":null}""",
    ).asJsonObject

    @Test
    fun `observed rejection is recognized without declaring session expired`() {
        assertTrue(isRepairAuthRejection(rejected))
        val error = repairAuthRejected("获取报修列表", rejected)
        assertTrue(error.technicalDetail.contains("REPAIR_REJECTED"))
        assertTrue(error.technicalDetail.contains("code=非法访问"))
        assertFalse(error.isAuthExpired())
        assertFalse(error.message.orEmpty().contains("非法访问"))
    }

    @Test
    fun `ordinary failures and incomplete responses are not auth rejections`() {
        listOf(
            """{"code":"1","flag":false,"msg":"获取报修列表失败"}""",
            """{"code":"非法访问","flag":true}""",
            """{"code":"非法访问"}""",
            """{"code":"非法访问","flag":null}""",
            """{"flag":false,"msg":"非法访问"}""",
            """{}""",
        ).forEach { json ->
            assertFalse(json, isRepairAuthRejection(JsonParser.parseString(json).asJsonObject))
        }
    }
}
