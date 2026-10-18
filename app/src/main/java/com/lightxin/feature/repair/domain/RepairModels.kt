package com.lightxin.feature.repair.domain

/** 地址 / 报修类型的扁平节点：服务端用 parent/value 表达树。 */
data class RepairNode(
    val name: String,
    val value: String,
    val parent: String,
)

object RepairCascade {
    const val ROOT_PARENT = "0"

    fun roots(nodes: List<RepairNode>): List<RepairNode> =
        nodes.filter { it.parent.isBlank() || it.parent == ROOT_PARENT }

    fun childrenOf(nodes: List<RepairNode>, parent: String): List<RepairNode> =
        nodes.filter { it.parent == parent }

    /** 从根到 node 的链；孤立节点（父不存在）只返回自身，不抛异常。 */
    fun pathTo(nodes: List<RepairNode>, value: String): List<RepairNode> {
        val byValue = nodes.associateBy { it.value }
        val result = ArrayDeque<RepairNode>()
        var current = byValue[value]
        var guard = 0
        while (current != null && guard++ < 16) {
            result.addFirst(current)
            current = byValue[current.parent]
        }
        return result.toList()
    }

    fun levelOf(nodes: List<RepairNode>, value: String): Int = pathTo(nodes, value).size
}

data class RepairDraft(
    val xqdm: String = "",
    val lyId: String = "",
    val qyfjh: String = "",
    val xxdz: String = "",
    val sjh: String = "",
    val xm: String = "",
    val gzms: String = "",
    val bxlx: String = "",
    val tpIds: List<String> = emptyList(),
    val kssj: String = "",
    val jssj: String = "",
)

object RepairSubmitBuilder {
    private val phoneRegex = Regex("""^1\d{10}$""")

    /** 可选时间未选不传；tpId 逗号拼接；bxlx 由 UI 传末级 value。 */
    fun build(draft: RepairDraft): Map<String, String> = buildMap {
        put("xqdm", draft.xqdm)
        put("lyId", draft.lyId)
        put("qyfjh", draft.qyfjh)
        put("xxdz", draft.xxdz)
        put("sjh", draft.sjh)
        put("xm", draft.xm)
        put("gzms", draft.gzms)
        put("bxlx", draft.bxlx)
        put("tpId", draft.tpIds.joinToString(","))
        if (draft.kssj.isNotBlank()) put("kssj", draft.kssj)
        if (draft.jssj.isNotBlank()) put("jssj", draft.jssj)
    }

    fun validate(draft: RepairDraft): Map<String, String> = buildMap {
        if (draft.xqdm.isBlank() || draft.lyId.isBlank() || draft.qyfjh.isBlank()) {
            put("location", "请选择校区、楼栋和房间")
        }
        if (draft.xxdz.isBlank()) put("xxdz", "请填写详细地址")
        if (!phoneRegex.matches(draft.sjh)) put("sjh", "请输入 11 位手机号")
        if (draft.bxlx.isBlank()) put("bxlx", "请选择报修类型")
        if (draft.gzms.isBlank()) put("gzms", "请填写故障描述")
    }
}
