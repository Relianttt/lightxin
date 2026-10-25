package com.lightxin.feature.exam.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExamTermsTest {

    @Test
    fun `options are ordered by term time descending`() {
        val years = listOf(
            SchoolYear(display = "2024-2025", value = "2024"),
            SchoolYear(display = "2025-2026", value = "2025"),
        )

        val options = examTermOptions(years)

        assertEquals(
            listOf(
                "2025-2026 第二学期",
                "2025-2026 第一学期",
                "2024-2025 第二学期",
                "2024-2025 第一学期",
            ),
            options.map { it.label },
        )
        assertEquals("2025|2", options.first().value)
    }

    @Test
    fun `year start falls back to display`() {
        val year = SchoolYear(display = "2023-2024", value = "xq-2023")
        assertEquals(2023, examYearStart(year))

        val unparsable = SchoolYear(display = "未知", value = "")
        assertEquals(null, examYearStart(unparsable))
        // 最近的学期在前，取不到年份的项沉到最后。
        val labels = examTermOptions(
            listOf(unparsable, SchoolYear("2025-2026", "2025")),
        ).map { it.label }
        assertEquals("2025-2026 第二学期", labels.first())
        assertEquals("2025-2026 第一学期", labels[1])
    }
}
