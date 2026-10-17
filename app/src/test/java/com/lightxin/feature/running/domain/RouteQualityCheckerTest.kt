package com.lightxin.feature.running.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteQualityCheckerTest {

    @Test
    fun `a short loop inside the campus passes`() {
        val points = loopAround(0.0, 0.0, meters = 200.0)

        val result = RouteQualityChecker.evaluate(points)

        assertEquals(RouteQualityStatus.PASS, result.status)
        assertEquals(null, result.message)
    }

    @Test
    fun `a track that has drifted off campus is flagged`() {
        val points = loopAround(3_000.0, 3_000.0, meters = 200.0)

        val result = RouteQualityChecker.evaluate(points)

        assertEquals(RouteQualityStatus.WARNING, result.status)
        assertEquals(true, result.message?.contains("超出校园范围"))
    }

    @Test
    fun `teleporting segments are flagged`() {
        val points = buildList {
            repeat(20) { add(RunningCampus.pointAt(it * 5.0, 0.0)) }
            repeat(20) { add(RunningCampus.pointAt(100.0 + it * 400.0, 0.0)) }
        }

        val result = RouteQualityChecker.evaluate(points)

        assertEquals(RouteQualityStatus.WARNING, result.status)
        assertEquals(true, result.message?.contains("大跳段"))
    }

    @Test
    fun `a track that barely moves is flagged`() {
        val anchor = RunningCampus.pointAt(0.0, 0.0)
        val points = List(40) { anchor.copy(longitude = anchor.longitude + it * 1e-8) }

        val result = RouteQualityChecker.evaluate(points)

        assertEquals(RouteQualityStatus.WARNING, result.status)
        assertEquals(true, result.message?.contains("有效移动段"))
    }

    private fun loopAround(offsetLatMeters: Double, offsetLngMeters: Double, meters: Double): List<TrackPoint> =
        buildList {
            repeat(40) { i ->
                add(
                    RunningCampus.pointAt(
                        offsetLatMeters + meters * kotlin.math.sin(i * Math.PI / 20.0),
                        offsetLngMeters + meters * kotlin.math.cos(i * Math.PI / 20.0),
                    ),
                )
            }
        }
}
