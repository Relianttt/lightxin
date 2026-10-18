package com.lightxin.feature.repair.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairStatusTest {

    @Test
    fun `已完工的单按 wcqk 修正过期状态`() {
        // HAR 20251225024：列表 dqzt=4，详情 dqzt=9 待处理，但 wcqk=1 / fwpj=1。
        val (code, name) = normalizedRepairStatus(
            code = "9",
            name = "待处理",
            wcqk = "1",
            fwpj = "1",
        )
        assertEquals("4", code)
        assertEquals("维修完成", name)
    }

    @Test
    fun `未完工的待处理保持原状`() {
        val (code, name) = normalizedRepairStatus(
            code = "9",
            name = "待处理",
            wcqk = "",
            fwpj = "",
        )
        assertEquals("9", code)
        assertEquals("待处理", name)
    }

    @Test
    fun `终态不因完成标志改写`() {
        val (code, name) = normalizedRepairStatus(code = "7", name = "已废单", wcqk = "1", fwpj = "")
        assertEquals("7", code)
        assertEquals("已废单", name)
    }

    @Test
    fun `终态码都算已结束`() {
        listOf("4", "5", "6", "7", "8", "10").forEach { code ->
            assertTrue("$code 应为终态", code in REPAIR_ENDED_CODES)
        }
        assertFalse("1" in REPAIR_ENDED_CODES)
        assertFalse("9" in REPAIR_ENDED_CODES)
        assertFalse("11" in REPAIR_ENDED_CODES)
    }
}
