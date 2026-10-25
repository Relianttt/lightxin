package com.lightxin.feature.schedule.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ScheduleParserTest {
    private val gson = Gson()

    @Test fun actualWeekFieldsPreserveZeroAndNonContiguousTail() {
        val response = gson.fromJson("""{"flag":true,"data":{"week":"17","schoolYear":"2025","schoolTerm":"2"},"rows":[{"value":"0","text":"第0周"},{"value":"2"},{"value":"17"}]}""", WeekListResponse::class.java)
        assertEquals(listOf(0, 2, 17), response.toWeekInfo().weekNumbers)
        assertEquals(3, response.toWeekInfo().totalWeeks)
    }

    @Test fun invalidSemesterNeverBecomesDefaultWeekOne() {
        for (json in listOf(
            """{"flag":true,"data":{},"rows":[]}""",
            """{"flag":false,"data":{"week":"1"},"rows":[]}""",
            """{"data":{"week":"1"},"rows":[]}""",
        )) assertTrue(runCatching { gson.fromJson(json, WeekListResponse::class.java).toWeekInfo() }.isFailure)
    }

    @Test fun explicitEmptyIsValidButMissingOrRejectedRowsAreNot() {
        assertTrue(TimeTableResponse(emptyList(), true).toScheduleData().courses.isEmpty())
        assertTrue(runCatching { TimeTableResponse(null, true).toScheduleData() }.isFailure)
        assertTrue(runCatching { TimeTableResponse(emptyList(), false).toScheduleData() }.isFailure)
        assertTrue(runCatching { TimeTableResponse(emptyList(), null).toScheduleData() }.isFailure)
        assertTrue(runCatching { TimeTableResponse(listOf(DaySchedule("1", null, null)), true).toScheduleData() }.isFailure)
    }

    @Test fun brokenCourseCannotBecomeAValidEmptyWeekAndSectionZeroIsAllowed() {
        fun response(start: String) = TimeTableResponse(listOf(DaySchedule("1", listOf(
            CourseVo("课", start, "1", "室", "师", null),
        ), "10/19")), true)
        assertTrue(runCatching { response("broken").toScheduleData() }.isFailure)
        assertEquals(0, response("0").toScheduleData().courses.single().startSection)
    }

    @Test fun explicitBlankGridCellsProduceAKnownEmptyWeekWithDates() {
        val blank = """{"kcmc":"","ksjc":"","jsjc":"","jsmc":"","id":"","kcdm":"","jxbh":"","kclbdm":"","mc":"","sksj":"","sort":1}"""
        val rows = (1..7).joinToString(",") { day ->
            """{"xq":"$day","rq":"10/${18 + day}","kcVoList":[${List(13) { blank }.joinToString(",")}]}"""
        }
        val data = gson.fromJson("""{"flag":true,"rows":[$rows]}""", TimeTableResponse::class.java).toScheduleData()
        assertTrue(data.courses.isEmpty())
        assertEquals(7, data.weekDates.size)
        assertEquals("10/19", data.weekDates[1])
    }

    @Test fun blankCellsDoNotHideRealCourses() {
        val blank = CourseVo("", "", "", "", null, null)
        val course = CourseVo("课", "3", "4", "室", "师", "code")
        val data = TimeTableResponse(listOf(DaySchedule("2", listOf(blank, course, blank), "10/20")), true)
            .toScheduleData()
        assertEquals(1, data.courses.size)
        assertEquals("课", data.courses.single().name)
        assertEquals(3, data.courses.single().startSection)
    }

    @Test fun incompleteCoursesAndMissingFieldsAreNotBlankGridCells() {
        for (vo in listOf(
            CourseVo("课", "", "", "", null, null),
            CourseVo("", "1", "2", "", null, null),
            CourseVo("", "", "", "室", null, null),
            CourseVo("", "", "", "", "师", null),
            CourseVo("", "", "", "", null, "code"),
            CourseVo(null, null, null, null, null, null),
        )) {
            val response = TimeTableResponse(listOf(DaySchedule("1", listOf(vo), "10/19")), true)
            assertTrue(runCatching { response.toScheduleData() }.isFailure)
        }
    }
}
