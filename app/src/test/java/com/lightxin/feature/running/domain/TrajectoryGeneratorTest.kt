package com.lightxin.feature.running.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrajectoryGeneratorTest {

    private val startMillis = 1_700_000_000_000L
    private val config = SimConfig(
        distanceKm = 3.2,
        durationMinutes = 24,
        startTimeMillis = startMillis,
    )

    @Test
    fun `builtin trajectory advances by small steps instead of jumping between vertices`() {
        val points = TrajectoryGenerator.generate(config).points

        assertTrue(points.size >= 2)
        points.indices.drop(1).forEach { i ->
            val meters = GeoDistance.metersBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
            assertTrue("第 $i 段长 ${meters.toInt()} m，超出采样步长容差", meters in 1.0..45.0)
        }
    }

    @Test
    fun `builtin trajectory stays inside the campus bounding box`() {
        TrajectoryGenerator.generate(config).points.forEach { point ->
            assertTrue(point.latitude in RunningCampus.minLatitude..RunningCampus.maxLatitude)
            assertTrue(point.longitude in RunningCampus.minLongitude..RunningCampus.maxLongitude)
        }
    }

    @Test
    fun `timestamps run from the start time across the configured duration`() {
        val points = TrajectoryGenerator.generate(config).points
        val durationMillis = config.durationMinutes * 60_000L

        assertEquals(startMillis, points.first().timestampMillis)
        val spanMillis = points.last().timestampMillis - startMillis
        assertTrue("实际时间跨度 $spanMillis ms", spanMillis in (durationMillis - 2_000)..durationMillis)
    }

    @Test
    fun `same config regenerates the same trajectory`() {
        assertEquals(
            TrajectoryGenerator.generate(config).points,
            TrajectoryGenerator.generate(config).points,
        )
    }

    @Test
    fun `both trajectory sources are wgs84 and need the upload conversion`() {
        assertFalse(TrajectoryGenerator.generate(config).isBd09)
        assertFalse(TrajectoryGenerator.generateFromTemplate(config, template(loopPoints())).isBd09)
    }

    @Test
    fun `a template with fewer than two points produces no trajectory`() {
        val single = template(listOf(RunningCampus.pointAt(0.0, 0.0)))

        assertTrue(TrajectoryGenerator.generateFromTemplate(config, single).points.isEmpty())
    }

    private fun loopPoints(): List<TrackPoint> = listOf(
        RunningCampus.pointAt(0.0, 0.0),
        RunningCampus.pointAt(200.0, 0.0),
        RunningCampus.pointAt(200.0, 200.0),
        RunningCampus.pointAt(0.0, 200.0),
    )

    private fun template(points: List<TrackPoint>) = RouteTemplate(
        id = "t1",
        name = "测试环线",
        createdAtMillis = startMillis,
        updatedAtMillis = startMillis,
        totalDistanceMeters = 800.0,
        durationSeconds = 300L,
        source = RouteTemplateSource.TEMPLATE_RECORDING,
        isDefault = true,
        points = points,
    )
}
