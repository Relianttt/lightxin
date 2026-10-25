package com.lightxin.feature.labor

import com.lightxin.feature.labor.domain.ActivityRecord
import com.lightxin.feature.labor.ui.laborHasMore
import com.lightxin.feature.labor.ui.mergeLaborActivities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaborPagingTest {
    @Test
    fun `total stops pagination even when the server returns a full page`() {
        assertFalse(laborHasMore(28, 100, 28, 28, 28))
        assertFalse(laborHasMore(100, 100, 100, 100, 100))
        assertTrue(laborHasMore(100, 100, 100, 100, 128))
        assertTrue(laborHasMore(28, 100, 28, 28, 128))
    }

    @Test
    fun `missing total falls back to raw page size`() {
        assertTrue(laborHasMore(100, 100, 100, 100, null))
        assertFalse(laborHasMore(28, 100, 28, 28, null))
    }

    @Test
    fun `repeated or empty page always stops pagination`() {
        assertFalse(laborHasMore(100, 100, 100, 0, 128))
        assertFalse(laborHasMore(100, 100, 100, 0, null))
        assertFalse(laborHasMore(0, 100, 0, 0, 28))
    }

    @Test
    fun `identity includes type and deduplicates within and across pages`() {
        val first = record("1", "1")
        val otherType = record("1", "6")
        val merged = mergeLaborActivities(listOf(first), listOf(first, otherType, otherType))
        assertEquals(listOf("1:1", "6:1"), merged.map { it.key })
    }

    @Test
    fun `dates sort descending across pages with malformed dates last`() {
        val march = record("march", date = "2025-03-01 09:00:00")
        val april = record("april", date = "2025-04-30 09:00:00")
        val latest = record("latest", date = "2026-03-01 09:00:00")
        val invalid = record("invalid", date = "2026-02-30 09:00:00")
        val missing = record("missing", date = "")
        assertEquals(
            listOf(latest, april, march, invalid, missing),
            mergeLaborActivities(listOf(march, invalid), listOf(april, missing, latest)),
        )
    }

    private fun record(id: String, type: String = "1", date: String = "2026-03-01 09:00:00") =
        ActivityRecord(id, "", type, "Activity", 1.0, date)
}
