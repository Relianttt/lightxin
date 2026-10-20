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
}
