package com.lightxin.feature.schedule.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekIndicatorTest {
    private val a = IndicatorSpan(0f, 60f)
    private val b = IndicatorSpan(68f, 128f)

    @Test fun landsExactlyOnBothEnds() {
        assertEquals(a, stretchedSpan(a, b, 0f))
        assertEquals(b, stretchedSpan(a, b, 1f))
    }

    @Test fun stretchesWiderThanEitherChipMidway() {
        val mid = stretchedSpan(a, b, 0.5f)
        assertTrue(mid.end - mid.start > b.end - b.start)
        assertTrue(mid.start < b.start && mid.end > a.end)
    }

    @Test fun leadingEdgeMovesFirst() {
        val early = stretchedSpan(a, b, 0.2f)
        // 往右：右边已走的比例大于左边
        assertTrue((early.end - a.end) / (b.end - a.end) > (early.start - a.start) / (b.start - a.start))
    }

    @Test fun stretchPeaksInTheMiddle() {
        assertEquals(0f, stretchAmount(0f), 1e-6f)
        assertEquals(1f, stretchAmount(0.5f), 1e-6f)
        assertEquals(0f, stretchAmount(1f), 1e-6f)
    }

    @Test fun emphasisFadesWithDistance() {
        assertEquals(1f, chipEmphasis(3f, 3), 1e-6f)
        assertEquals(0.25f, chipEmphasis(3.75f, 3), 1e-6f)
        assertEquals(0f, chipEmphasis(5f, 3), 1e-6f)
    }
}
