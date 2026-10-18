package com.lightxin.feature.leave.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaveListLogicTest {

    @Test
    fun `已撤回和撤销都不算进行中`() {
        assertTrue(isWithdrawn("已撤回"))
        assertTrue(isWithdrawn("已撤销"))
        assertFalse(isWithdrawn("待销假"))
        assertFalse(isWithdrawn("已完结"))
    }

    @Test
    fun `分页列表的 createDate 可以解析并排序`() {
        val newer = parseLeaveTime("2026-06-01 09:06:23")
        val older = parseLeaveTime("2025-02-20 11:28:00")
        assertTrue(newer != null && older != null && newer.isAfter(older))
        assertEquals(9, newer?.hour)
        assertEquals(6, newer?.monthValue)
    }

    @Test
    fun `无法解析的时间返回 null 而不是抛错`() {
        assertNull(parseLeaveTime(""))
        assertNull(parseLeaveTime("昨天"))
    }
}
