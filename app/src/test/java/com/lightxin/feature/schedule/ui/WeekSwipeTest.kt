package com.lightxin.feature.schedule.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekSwipeTest {
    private val weeks = listOf(0, 1, 2, 3, 4)

    @Test fun swipeLeftMovesToNextWeek() {
        assertEquals(3, weekAfterSwipe(weeks, selected = 2, step = 1))
    }

    @Test fun swipeRightMovesToPreviousWeek() {
        assertEquals(1, weekAfterSwipe(weeks, selected = 2, step = -1))
    }

    @Test fun stopsAtBothEnds() {
        assertNull(weekAfterSwipe(weeks, selected = weeks.first(), step = -1))
        assertNull(weekAfterSwipe(weeks, selected = weeks.last(), step = 1))
    }

    @Test fun ignoresUnknownSelectionAndZeroStep() {
        assertNull(weekAfterSwipe(weeks, selected = 9, step = 1))
        assertNull(weekAfterSwipe(weeks, selected = 2, step = 0))
        assertNull(weekAfterSwipe(emptyList(), selected = 2, step = 1))
    }
}
