package com.lightxin.feature.schedule.ui

import kotlin.math.roundToInt

/** 教室最多允许的行数（块再高也只留这么多给教室，剩下的都给课名）。 */
internal const val ROOM_MAX_LINES = 3

/** 教室最多占块内高的比例，避免 1 节高的块把高度全让给教室。 */
private const val ROOM_HEIGHT_RATIO = 0.45f

/**
 * 课程块内的行数预算：返回 课名行数 to 教室行数。
 *
 * 块高由节次数决定，能放几行文字必须按块高算。写死 `maxLines` 会出现两种截断：
 * 2 节高的块只画 3 行课名（大半个块空着）、1 节高的块硬塞 1 行教室（教室只剩 3 个字加省略号）。
 *
 * 规则：先给教室不超过块内高 45% 的行数（四舍五入，最多 [ROOM_MAX_LINES] 行），其余全给课名，
 * 保证 `nameLines * nameLineDp + roomLines * roomLineDp <= innerHeightDp`，文字永不撑破色块。
 *
 * @param innerHeightDp 色块内可排版高度（已扣掉上下内边距）
 * @param nameLineDp 课名单行高度（sp 换算成 dp 后的值，含系统字体缩放）
 * @param roomLineDp 教室单行高度，同上
 */
internal fun courseBlockLines(
    innerHeightDp: Float,
    nameLineDp: Float,
    roomLineDp: Float,
    hasRoom: Boolean,
): Pair<Int, Int> {
    if (innerHeightDp <= 0f || nameLineDp <= 0f) return 1 to 0
    if (!hasRoom || roomLineDp <= 0f) {
        return (innerHeightDp / nameLineDp).toInt().coerceAtLeast(1) to 0
    }
    val preferred = (innerHeightDp * ROOM_HEIGHT_RATIO / roomLineDp).roundToInt()
    // 课名至少留 1 行，剩下的才轮到教室
    val roomLimit = ((innerHeightDp - nameLineDp) / roomLineDp).toInt()
    val roomLines = preferred.coerceIn(1, ROOM_MAX_LINES)
        .coerceAtMost(roomLimit)
        .coerceAtLeast(1)
    val nameLines = ((innerHeightDp - roomLines * roomLineDp) / nameLineDp).toInt()
        .coerceAtLeast(1)
    return nameLines to roomLines
}

// 服务端教室格式形如“博识楼（A4）S201”，楼名和楼号的括号全半角都可能不配对。
private val BUILDING_WITH_CODE = Regex("""^[^（(\s]*楼\s*[（(]\s*([^）)]+?)\s*[）)]\s*(.*)$""")
private val BUILDING_ONLY = Regex("""^[^（(\s]*楼\s*(.*)$""")

/**
 * 课表格子里的教室短标：只留楼号 + 房间号（`博识楼（A4）S201` → `A4 S201`）。
 *
 * 格子里一行只放得下 3-4 个汉字，“博识楼（A4）S201”必然被省略号盖掉；
 * 完整楼名留给课程详情弹窗，那里不差宽度。
 */
internal fun shortRoomLabel(room: String): String {
    val text = room.trim()
    if (text.isEmpty()) return text
    BUILDING_WITH_CODE.find(text)?.let { match ->
        val code = match.groupValues[1].trim()
        val rest = match.groupValues[2].trim()
        return if (rest.isEmpty()) code else "$code $rest"
    }
    BUILDING_ONLY.find(text)?.let { match ->
        val rest = match.groupValues[1].trim()
        if (rest.isNotEmpty()) return rest
    }
    return text
}
