package com.lightxin.feature.running.domain

import org.junit.Assert.*
import org.junit.Test

class PolylineSamplerTest {
    private val points = listOf(0.0, 13.0, 48.0, 72.0, 100.0).mapIndexed { i, meters ->
        RunningCampus.pointAt(meters, 0.0).copy(timestampMillis = listOf(0L, 50L, 800L, 1200L, 9000L)[i])
    }
    private fun distance(a: TrackPoint, b: TrackPoint) = GeoDistance.metersBetween(a.latitude, a.longitude, b.latitude, b.longitude)
    private fun length(points: List<TrackPoint>) = points.zipWithNext(::distance).sum()
    private fun positions(points: List<TrackPoint>) = points.map { it.latitude to it.longitude }

    @Test fun openRouteBouncesWithoutDuplicatingEndpointsOrTeleporting() {
        val replay = PolylineSampler.sampleFromTemplate(points, length(points) * 3, 10_000, 60)
        val indices = listOf(0, 1, 2, 3, 4, 3, 2, 1, 0, 1, 2, 3, 4)
        assertEquals(positions(indices.map(points::get)), positions(replay))
        assertEquals(10_000L, replay.first().timestampMillis)
        assertEquals(70_000L, replay.last().timestampMillis)
        assertTrue(replay.zipWithNext().all { (a, b) -> b.timestampMillis > a.timestampMillis })
    }

    @Test fun preservesUnevenOriginalPositionsAndUsesDistanceBasedTiming() {
        val replay = PolylineSampler.sampleFromTemplate(points, length(points), 1234, 100)
        assertEquals(positions(points), positions(replay))
        val expected = (distance(points[0], points[1]) / length(points) * 100_000).toLong()
        assertEquals(expected.toDouble(), (replay[1].timestampMillis - 1234).toDouble(), 1.0)
        assertEquals(101_234L, replay.last().timestampMillis)
        assertEquals(replay, PolylineSampler.sampleFromTemplate(points, length(points), 1234, 100))
    }

    @Test fun interpolatesOnlyFinalPartialSegmentIncludingOnReverseLeg() {
        for (target in listOf(distance(points[0], points[1]) + 7.0, length(points) + 10.0)) {
            val replay = PolylineSampler.sampleFromTemplate(points, target, 0, 100)
            assertEquals(target, length(replay), 0.0001)
            val complete = if (target < length(points)) points.take(2) else points
            assertEquals(positions(complete), positions(replay.dropLast(1)))
            assertEquals(100_000L, replay.last().timestampMillis)
        }
    }

    @Test fun stationaryRecordedSamplesArePreservedButDoNotReuseTheirTimestamps() {
        val recorded = listOf(points[0], points[0], points[1].copy(timestampMillis = -1), points[2])
        val replay = PolylineSampler.sampleFromTemplate(recorded, length(recorded), 1000, 10)
        assertEquals(positions(recorded), positions(replay))
        assertTrue(replay.zipWithNext().all { (a, b) -> b.timestampMillis > a.timestampMillis })
        assertEquals(11_000L, replay.last().timestampMillis)
    }

    @Test fun rejectsInvalidInputInsteadOfGeneratingFallbackPoints() {
        for (bad in listOf(emptyList(), points.take(1), listOf(points[0], points[0]),
            listOf(points[0], points[1].copy(latitude = Double.NaN)),
            listOf(points[0], points[1].copy(longitude = 181.0)))) {
            assertThrows(IllegalArgumentException::class.java) {
                PolylineSampler.sampleFromTemplate(bad, 50.0, 0, 10)
            }
        }
        for (distance in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                PolylineSampler.sampleFromTemplate(points, distance, 0, 10)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PolylineSampler.sampleFromTemplate(points, 100.0, Long.MAX_VALUE, 10)
        }
        val dense = (0..2000).map { RunningCampus.pointAt(it * 0.01, 0.0) }
        assertThrows(IllegalArgumentException::class.java) {
            PolylineSampler.sampleFromTemplate(dense, length(dense), 0, 1)
        }
    }
}
