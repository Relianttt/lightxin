package com.lightxin.feature.schedule.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseBlockLabelsTest {
    // ── 行数预算 ──
    // 与 ScheduleScreen 里的排版常量一致：单节 58dp、上下内边距 3dp、课名 13dp/行、教室 11dp/行
    private val cellDp = 58f
    private val paddingDp = 3f
    private val nameLine = 13f
    private val roomLine = 11f

    private fun lines(rows: Int, hasRoom: Boolean = true, scale: Float = 1f) = courseBlockLines(
        innerHeightDp = rows * cellDp - paddingDp * 2,
        nameLineDp = nameLine * scale,
        roomLineDp = roomLine * scale,
        hasRoom = hasRoom,
    )

    @Test fun singleSectionBlockShowsTwoNameLinesAndTwoRoomLines() {
        assertEquals(2 to 2, lines(rows = 1))
    }

    @Test fun twoSectionBlockGetsRoomForLongCourseNames() {
        val (name, room) = lines(rows = 2)
        assertEquals(5, name)
        assertEquals(3, room)
    }

    @Test fun deviceFontScaleStillAffordsTwoRoomLinesInSingleSectionBlock() {
        // 真机 font_scale=1.06 时行高跟着涨，45% 预算刚好卡在 1.9 行，四舍五入之后才不会只给教室 1 行
        assertEquals(2 to 2, lines(rows = 1, scale = 1.06f))
    }

    @Test fun roomNeverExceedsCap() {
        for (rows in 1..10) assertTrue(lines(rows).second <= ROOM_MAX_LINES)
    }

    @Test fun textNeverOverflowsBlockHeight() {
        // 系统字体缩放到 2.0x 也不能把色块撑破
        for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 2f)) {
            for (rows in 1..10) {
                val (name, room) = lines(rows = rows, scale = scale)
                val used = name * nameLine * scale + room * roomLine * scale
                val inner = rows * cellDp - paddingDp * 2
                assertTrue("rows=$rows scale=$scale used=$used inner=$inner", used <= inner)
                assertTrue(name >= 1)
                assertTrue(room >= 1)
            }
        }
    }

    @Test fun blockWithoutRoomGivesAllLinesToName() {
        val (name, room) = lines(rows = 1, hasRoom = false)
        assertEquals(0, room)
        assertEquals(4, name)
    }
    @Test fun degenerateHeightsFallBackToOneNameLine() {
        assertEquals(1 to 0, courseBlockLines(innerHeightDp = 0f, nameLineDp = 13f, roomLineDp = 11f, hasRoom = true))
    }

    // ── 教室短标 ──

    @Test fun keepsOnlyBuildingCodeAndRoom() {
        assertEquals("A4 S201", shortRoomLabel("博识楼（A4）S201"))
        assertEquals("A5 N303", shortRoomLabel("博行楼（A5）N303"))
    }

    @Test fun toleratesMismatchedFullWidthAndHalfWidthParens() {
        // 真实数据里求是楼这条是全角左括号配半角右括号
        assertEquals("A7 S501", shortRoomLabel("求是楼（A7)  S501"))
        assertEquals("A1 N106", shortRoomLabel("博雅楼(A1）N106"))
    }

    @Test fun keepsRoomWhenBuildingHasNoCode() {
        assertEquals("N205", shortRoomLabel("博学楼N205"))
    }

    @Test fun leavesUnknownPlaceAndEmptyValueAlone() {
        assertEquals("体育馆", shortRoomLabel("体育馆"))
        assertEquals("", shortRoomLabel("   "))
    }
}
