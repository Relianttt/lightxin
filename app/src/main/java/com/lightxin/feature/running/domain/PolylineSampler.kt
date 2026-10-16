package com.lightxin.feature.running.domain

import kotlin.math.roundToLong
import kotlin.math.abs

/** Replay recorded positions forward and backward; interpolate only the final partial segment. */
object PolylineSampler {
    private const val MAX_REPLAY_POINTS = 100_000

    fun sampleFromTemplate(
        templatePoints: List<TrackPoint>,
        targetDistanceMeters: Double,
        startTimeMillis: Long,
        durationSeconds: Long,
    ): List<TrackPoint> {
        require(templatePoints.size >= 2) { "模板至少需要两个轨迹点" }
        require(templatePoints.all { it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }) { "模板含无效坐标，请重新录制" }
        require(targetDistanceMeters.isFinite() && targetDistanceMeters > 0) { "目标距离无效" }
        require(durationSeconds > 0 && durationSeconds <= Long.MAX_VALUE / 1000) { "回放时长无效" }
        val durationMillis = durationSeconds * 1000
        require(startTimeMillis <= Long.MAX_VALUE - durationMillis) { "回放时间超出范围" }
        val lengths = templatePoints.zipWithNext { a, b -> distance(a, b) }
        require(lengths.all { it.isFinite() } && lengths.sum() > 0.0) { "模板没有有效移动距离，请重新录制" }

        val positions = arrayListOf(templatePoints.first())
        val cumulative = arrayListOf(0.0)
        var travelled = 0.0
        var index = 0
        var direction = 1
        while (travelled < targetDistanceMeters) {
            require(positions.size < MAX_REPLAY_POINTS) { "模板点位过密或回放距离过长，请缩短回放距离" }
            val next = index + direction
            val segmentLength = lengths[minOf(index, next)]
            val remaining = targetDistanceMeters - travelled
            val atEndpoint = abs(segmentLength - remaining) <= 1e-7
            val reachesEnd = segmentLength >= remaining || atEndpoint
            positions += if (reachesEnd && !atEndpoint && segmentLength > remaining) {
                interpolateEnd(templatePoints[index], templatePoints[next], remaining)
            } else templatePoints[next]
            travelled = if (reachesEnd) targetDistanceMeters else travelled + segmentLength
            cumulative += travelled
            index = next
            if (index == templatePoints.lastIndex || index == 0) direction = -direction
        }

        require(durationMillis >= positions.lastIndex) { "回放时长不足以容纳全部原始点位" }
        var previous = -1L
        return positions.mapIndexed { i, point ->
            val ideal = (cumulative[i] / targetDistanceMeters * durationMillis).roundToLong()
            // Reserve at least one millisecond for each remaining point, including stationary samples.
            val elapsed = if (i == positions.lastIndex) durationMillis
                else ideal.coerceIn(previous + 1, durationMillis - (positions.lastIndex - i))
            previous = elapsed
            point.copy(timestampMillis = startTimeMillis + elapsed)
        }
    }

    private fun distance(a: TrackPoint, b: TrackPoint): Double =
        GeoDistance.metersBetween(a.latitude, a.longitude, b.latitude, b.longitude)

    private fun interpolateEnd(from: TrackPoint, to: TrackPoint, remaining: Double): TrackPoint {
        var low = 0.0
        var high = 1.0
        fun at(t: Double) = TrackPoint(
            from.latitude + (to.latitude - from.latitude) * t,
            from.longitude + (to.longitude - from.longitude) * t,
        )
        repeat(48) {
            val mid = (low + high) / 2
            if (distance(from, at(mid)) < remaining) low = mid else high = mid
        }
        return at((low + high) / 2)
    }
}
