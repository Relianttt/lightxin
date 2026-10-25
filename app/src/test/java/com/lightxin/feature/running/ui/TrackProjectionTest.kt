package com.lightxin.feature.running.ui

import com.lightxin.feature.running.domain.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class TrackProjectionTest {

    /** 以米计的正方形：纬度跨度 dLat，经度跨度 dLat / cos(lat)，投影后应接近正方形。 */
    private fun meterSquare(lat: Double, lng: Double, dLat: Double = 0.0005): List<TrackPoint> {
        val dLng = dLat / cos(Math.toRadians(lat))
        return listOf(
            TrackPoint(lat, lng),
            TrackPoint(lat + dLat, lng),
            TrackPoint(lat + dLat, lng + dLng),
            TrackPoint(lat, lng + dLng),
            TrackPoint(lat, lng),
        )
    }

    @Test
    fun `projection keeps square aspect ratio`() {
        val projected = projectTrack(meterSquare(31.0, 121.0), width = 1000f, height = 1000f)
        assertEquals(5, projected.size)

        val width = projected.maxOf { it.x } - projected.minOf { it.x }
        val height = projected.maxOf { it.y } - projected.minOf { it.y }
        val ratio = width / height
        assertTrue("宽高比应为 1，实际 $ratio", abs(ratio - 1f) < 0.02f)
    }

    @Test
    fun `all projected points stay inside canvas`() {
        val projected = projectTrack(meterSquare(31.0, 121.0), width = 400f, height = 200f)
        projected.forEach { point ->
            assertTrue("x 越界：${point.x}", point.x in 0f..400f)
            assertTrue("y 越界：${point.y}", point.y in 0f..200f)
        }
    }

    @Test
    fun `empty and degenerate input does not crash`() {
        assertEquals(0, projectTrack(emptyList(), width = 100f, height = 100f).size)
        val single = listOf(TrackPoint(31.0, 121.0))
        assertEquals(1, projectTrack(single, width = 100f, height = 100f).size)
    }
}
