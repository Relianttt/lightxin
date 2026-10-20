package com.lightxin.feature.schedule.data

import com.lightxin.feature.schedule.data.local.SemesterSnapshot
import com.lightxin.feature.schedule.domain.WeekInfo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SemesterSnapshotTest {
    @Test fun sundayToMondayUsesNaturalWeekAndCrossesYears() {
        val snapshot = SemesterSnapshot(WeekInfo(7, 4, "2025", "2", listOf(0, 7, 8, 17)), LocalDate.parse("2025-12-28"), 1)
        assertEquals(7, snapshot.estimatedInfo(LocalDate.parse("2025-12-28")).currentWeek)
        assertEquals(8, snapshot.estimatedInfo(LocalDate.parse("2025-12-29")).currentWeek)
        assertEquals(8, snapshot.estimatedInfo(LocalDate.parse("2026-01-01")).currentWeek)
        assertEquals(17, snapshot.estimatedInfo(LocalDate.parse("2027-01-01")).currentWeek)
    }
}
