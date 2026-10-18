package com.lightxin.feature.repair.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairCascadeTest {

    private val nodes = listOf(
        RepairNode("新芜校区", "02", "0"),
        RepairNode("A36宿舍楼", "ly-36", "02"),
        RepairNode("南楼", "room-s", "ly-36"),
        RepairNode("中楼", "room-m", "ly-36"),
        RepairNode("A20宿舍楼", "ly-20", "02"),
        RepairNode("孤立节点", "orphan", "missing"),
        RepairNode("木", "type-wood", ""),
        RepairNode("房门锁", "type-lock", "type-wood"),
    )

    @Test
    fun `扁平节点组装三级链`() {
        assertEquals(listOf("02"), RepairCascade.roots(nodes.take(5)).map { it.value })
        assertEquals(listOf("ly-36", "ly-20"), RepairCascade.childrenOf(nodes, "02").map { it.value })
        assertEquals(listOf("room-s", "room-m"), RepairCascade.childrenOf(nodes, "ly-36").map { it.value })
        assertEquals(
            listOf("02", "ly-36", "room-s"),
            RepairCascade.pathTo(nodes, "room-s").map { it.value },
        )
        assertEquals(3, RepairCascade.levelOf(nodes, "room-s"))
    }

    @Test
    fun `零父值也视为根`() {
        assertTrue(RepairCascade.roots(nodes).any { it.value == "type-wood" })
        assertTrue(RepairCascade.roots(nodes).any { it.value == "02" })
    }

    @Test
    fun `孤立节点不崩且只返回自身`() {
        assertEquals(listOf("orphan"), RepairCascade.pathTo(nodes, "orphan").map { it.value })
        assertTrue(RepairCascade.childrenOf(nodes, "missing").any { it.value == "orphan" })
        assertEquals(emptyList<RepairNode>(), RepairCascade.childrenOf(nodes, "nope"))
    }
}
