package com.lightxin.feature.running.service

import com.lightxin.feature.running.domain.RunningStartInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningTrackerTest {

    private val startInfo = RunningStartInfo(exerciseId = "extra-1", memberId = "member-1")

    @Test
    fun `duration is frozen after stopCollecting so a retry uploads the same numbers`() {
        val tracker = RunningTracker()
        tracker.beginSession(startInfo, startTimeMillis = System.currentTimeMillis() - 600_000L)
        tracker.stopCollecting()

        val first = requireNotNull(tracker.snapshotForUpload())
        val second = requireNotNull(tracker.snapshotForUpload())

        assertTrue("锁定到的时长 ${first.durationSeconds}s", first.durationSeconds in 600L..601L)
        assertEquals(first.durationSeconds, second.durationSeconds)
    }

    @Test
    fun `template recording has no uploadable snapshot`() {
        val tracker = RunningTracker()
        tracker.beginTemplateSession()
        tracker.stopCollecting()

        assertNull(tracker.snapshotForUpload())
    }

    @Test
    fun `stopCollecting locks the track against later locations`() {
        val tracker = RunningTracker()
        tracker.beginSession(startInfo)
        tracker.stopCollecting()

        assertTrue(tracker.state.value.endTimeMillis != null)
        assertTrue(!tracker.state.value.isCollecting)
    }

    @Test
    fun `completeSession drops the locked snapshot`() {
        val tracker = RunningTracker()
        tracker.beginSession(startInfo)
        tracker.stopCollecting()
        tracker.completeSession()

        assertNull(tracker.snapshotForUpload())
    }
}
