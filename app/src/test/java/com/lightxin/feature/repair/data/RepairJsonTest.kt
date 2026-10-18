package com.lightxin.feature.repair.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairJsonTest {

    @Test
    fun `相对 fileUrl 补全为可加载地址`() {
        val obj = JsonParser.parseString(
            """{"fileList":[{"fileUrl":"group2/M00/87/4E/a.jpg"},{"fileFullName":"https://fs.aiit.edu.cn/group2/b.jpg"}]}""",
        ).asJsonObject
        assertEquals(
            listOf("https://fs.aiit.edu.cn/group2/M00/87/4E/a.jpg", "https://fs.aiit.edu.cn/group2/b.jpg"),
            RepairJson.photoUrls(obj),
        )
    }

    @Test
    fun `扁平节点组装并保留孤立节点`() {
        val root = JsonParser.parseString(
            """{"data":{"data":[{"name":"电","value":"e","parent":"0"},{"name":"灯","value":"l","parent":"e"},{"name":"孤儿","value":"o","parent":"x"}]}}""",
        ).asJsonObject
        val nodes = RepairJson.parseNodes(root)
        assertEquals(3, nodes.size)
        assertTrue(nodes.any { it.value == "l" && it.parent == "e" })
    }
}
