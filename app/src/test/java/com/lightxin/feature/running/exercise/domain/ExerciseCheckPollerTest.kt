package com.lightxin.feature.running.exercise.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseCheckPollerTest {

    @Test
    fun `success when check succeeded even before deadline`() {
        assertEquals(
            PollOutcome.SUCCESS,
            ExerciseCheckPoller.decide(checkSucceeded = true, nowMs = 100, deadlineMs = 1000),
        )
    }

    @Test
    fun `continue when not succeeded and before deadline`() {
        assertEquals(
            PollOutcome.CONTINUE,
            ExerciseCheckPoller.decide(checkSucceeded = false, nowMs = 100, deadlineMs = 1000),
        )
    }

    @Test
    fun `timeout when not succeeded and past deadline`() {
        assertEquals(
            PollOutcome.TIMEOUT,
            ExerciseCheckPoller.decide(checkSucceeded = false, nowMs = 1000, deadlineMs = 1000),
        )
    }

    @Test
    fun `success takes priority over deadline`() {
        assertEquals(
            PollOutcome.SUCCESS,
            ExerciseCheckPoller.decide(checkSucceeded = true, nowMs = 2000, deadlineMs = 1000),
        )
    }
}
