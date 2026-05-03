package com.lightxin.feature.running.domain

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 沿路线模板 polyline 采样生成模拟轨迹。
 *
 * 流程：
 *   1. 计算模板段长，累计出 polyline 总长
 *   2. 目标距离大于模板 → 循环拼接；小于 → 截取
 *   3. 按 sampleStepMeters 均匀在 polyline 上采样，线段内线性插值
 *   4. 时间戳按全程时长线性分配
 *   5. 加入低幅平滑抖动（sin/cos 噪声）避免锯齿
 */
object PolylineSampler {

    private const val MIN_TEMPLATE_METERS = 50.0

    fun sampleFromTemplate(
        templatePoints: List<TrackPoint>,
        targetDistanceMeters: Double,
        startTimeMillis: Long,
        durationSeconds: Long,
        sampleStepMeters: Double = 30.0,
        jitterMeters: Double = 3.0,
        seed: Long = startTimeMillis,
    ): List<TrackPoint> {
        if (templatePoints.size < 2 || targetDistanceMeters <= 0.0 || durationSeconds <= 0L) {
            return emptyList()
        }

        val segments = buildSegments(templatePoints)
        val templateLength = segments.sumOf { it.meters }
        if (templateLength < MIN_TEMPLATE_METERS) return emptyList()

        val random = Random(seed)
        // 起点偏移：给定一小段 0..min(templateLength, 200) 的随机偏移
        val startOffset = random.nextDouble(0.0, templateLength.coerceAtMost(200.0))

        val sampleCount = ceil(targetDistanceMeters / sampleStepMeters).toInt().coerceAtLeast(2) + 1
        val actualStep = targetDistanceMeters / (sampleCount - 1)

        val totalDurationMillis = durationSeconds * 1000L
        val interval = if (sampleCount <= 1) totalDurationMillis else totalDurationMillis / (sampleCount - 1)

        val result = ArrayList<TrackPoint>(sampleCount)
        for (i in 0 until sampleCount) {
            val distanceAlong = i * actualStep
            val effective = (startOffset + distanceAlong) % templateLength
            val base = locateOnPolyline(segments, effective)
            val timestamp = startTimeMillis + i * interval
            val jittered = applyJitter(base, jitterMeters, i, random)
            result += jittered.copy(timestampMillis = timestamp)
        }
        return result
    }

    private data class Segment(
        val from: TrackPoint,
        val to: TrackPoint,
        val meters: Double,
    )

    private fun buildSegments(points: List<TrackPoint>): List<Segment> {
        val result = ArrayList<Segment>(points.size - 1)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val meters = GeoDistance.metersBetween(a.latitude, a.longitude, b.latitude, b.longitude)
            if (meters > 0.0) result += Segment(a, b, meters)
        }
        return result
    }

    private fun locateOnPolyline(segments: List<Segment>, distanceMeters: Double): TrackPoint {
        var remaining = distanceMeters
        for (seg in segments) {
            if (remaining <= seg.meters) {
                val t = if (seg.meters <= 0.0) 0.0 else remaining / seg.meters
                return TrackPoint(
                    latitude = seg.from.latitude + (seg.to.latitude - seg.from.latitude) * t,
                    longitude = seg.from.longitude + (seg.to.longitude - seg.from.longitude) * t,
                )
            }
            remaining -= seg.meters
        }
        return segments.last().to
    }

    private fun applyJitter(point: TrackPoint, jitterMeters: Double, index: Int, random: Random): TrackPoint {
        if (jitterMeters <= 0.0) return point
        // 低频正弦噪声 + 少量随机扰动，避免锯齿
        val noiseLat = sin(index * 0.53) * jitterMeters * random.nextDouble(0.4, 1.0)
        val noiseLng = cos(index * 0.71) * jitterMeters * random.nextDouble(0.4, 1.0)
        val latOffset = noiseLat / 111_320.0
        val lngOffset = noiseLng / (111_320.0 * cos(Math.toRadians(point.latitude)))
        return point.copy(
            latitude = point.latitude + latOffset,
            longitude = point.longitude + lngOffset,
        )
    }
}
