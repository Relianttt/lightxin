package com.lightxin.feature.home.domain

import com.lightxin.feature.schedule.data.local.ScheduleWeekSnapshot
import com.lightxin.feature.schedule.domain.Course
import com.lightxin.feature.schedule.domain.ScheduleData
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TodayCourseLoaderTest {
    private fun week(name: String, section: Int) = ScheduleWeekSnapshot(ScheduleData(
        listOf(Course(name, section, section, "", "", 1)), emptyMap(),
    ), 1)

    @Test fun sundayTomorrowUsesNextWeekAndMissingIsNotConfirmedEmpty() {
        val sunday = LocalDate.parse("2026-10-25")
        val summary = summarizeCourses(sunday, 7, week("old monday", 1), week("new monday", 3))
        assertEquals(3, summary.tomorrowFirstSection)
        assertTrue(summary.tomorrowKnown)
        val unknown = summarizeCourses(sunday, 7, week("old monday", 1), null)
        assertNull(unknown.tomorrowFirstSection)
        assertFalse(unknown.tomorrowKnown)
        val empty = summarizeCourses(sunday, 7, week("old monday", 1), ScheduleWeekSnapshot(ScheduleData(emptyList(), emptyMap()), 1))
        assertTrue(empty.tomorrowKnown)
        assertNull(empty.tomorrowFirstSection)
    }

    @Test fun offlineOldSnapshotsStillSupplyCoursesWithoutAgeGate() {
        val monday = LocalDate.parse("2026-10-19")
        val summary = summarizeCourses(monday, 7, week("cached", 1), null)
        assertEquals("cached", summary.todayCourses.single().name)
        assertTrue(summary.todayKnown)
        assertEquals(1L, summary.lastSyncedAt)
    }
}
