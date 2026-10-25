package com.lightxin.feature.exam.domain

import org.junit.Assert.*
import org.junit.Test

class ExamSummaryTest {
    private fun course(credit: String, gpa: String) = ExamScore("", "课", "", credit, "", "", gpa, "", "")

    @Test fun weightedGpaUsesOnlyCoursesWithValidGpa() {
        val result = summarize(listOf(course("2", "4"), course("1", "1"), course("3", ""), course("0", "9")))
        assertEquals(3.0, result.weightedGpa!!, 0.001)
        assertEquals(6.0, result.totalCredit, 0.001)
        assertEquals(4, result.courseCount)
    }

    @Test fun noEligibleCourseHasNoGpa() {
        assertNull(summarize(emptyList()).weightedGpa)
        assertNull(summarize(listOf(course("0", "4"), course("2", ""))).weightedGpa)
        assertNull(summarize(listOf(course("NaN", "4"), course("2", "Infinity"))).weightedGpa)
    }
}
