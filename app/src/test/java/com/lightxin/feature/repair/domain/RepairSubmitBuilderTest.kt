package com.lightxin.feature.repair.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairSubmitBuilderTest {

    private val valid = RepairDraft(
        xqdm = "02",
        lyId = "ly-36",
        qyfjh = "room-s",
        xxdz = "A36M214",
        sjh = "18356220503",
        xm = "何宇翔",
        gzms = "门锁没电",
        bxlx = "type-lock",
        tpIds = listOf("f1", "f2"),
    )

    @Test
    fun `tpId 逗号拼接且未选时间不传`() {
        val body = RepairSubmitBuilder.build(valid)
        assertEquals("f1,f2", body["tpId"])
        assertFalse(body.containsKey("kssj"))
        assertFalse(body.containsKey("jssj"))
        assertEquals("type-lock", body["bxlx"])
    }

    @Test
    fun `选了时间才传`() {
        val body = RepairSubmitBuilder.build(valid.copy(kssj = "2026-06-12 10:00", jssj = "2026-06-12 11:00"))
        assertEquals("2026-06-12 10:00", body["kssj"])
        assertEquals("2026-06-12 11:00", body["jssj"])
    }

    @Test
    fun `五条校验文案齐全`() {
        val errors = RepairSubmitBuilder.validate(RepairDraft(sjh = "123"))
        assertEquals(
            setOf("location", "xxdz", "sjh", "bxlx", "gzms"),
            errors.keys,
        )
        assertTrue(errors.getValue("location").contains("校区"))
        assertTrue(errors.getValue("sjh").contains("手机号"))
    }

    @Test
    fun `手机号格式通过`() {
        assertTrue(RepairSubmitBuilder.validate(valid).isEmpty())
        assertTrue(RepairSubmitBuilder.validate(valid.copy(sjh = "28356220503")).containsKey("sjh"))
    }
}
