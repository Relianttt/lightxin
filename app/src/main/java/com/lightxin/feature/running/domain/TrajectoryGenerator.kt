package com.lightxin.feature.running.domain

import kotlin.math.cos
import kotlin.math.max
import kotlin.random.Random

object TrajectoryGenerator {

    private val campusRoutes = listOf(
        listOf(
            TrackPoint(31.141338, 118.637378),
            TrackPoint(31.141884, 118.637012),
            TrackPoint(31.142214, 118.636195),
            TrackPoint(31.142028, 118.635337),
            TrackPoint(31.141426, 118.634904),
            TrackPoint(31.140734, 118.635113),
            TrackPoint(31.140421, 118.635981),
            TrackPoint(31.140648, 118.636909),
        ),
        listOf(
            TrackPoint(31.140967, 118.634710),
            TrackPoint(31.141574, 118.634584),
            TrackPoint(31.142097, 118.634943),
            TrackPoint(31.142342, 118.635744),
            TrackPoint(31.142021, 118.636627),
            TrackPoint(31.141224, 118.636942),
            TrackPoint(31.140544, 118.636436),
            TrackPoint(31.140385, 118.635468),
        ),
        listOf(
            TrackPoint(31.141188, 118.635156),
            TrackPoint(31.141864, 118.635026),
            TrackPoint(31.142392, 118.635620),
            TrackPoint(31.142220, 118.636528),
            TrackPoint(31.141402, 118.637070),
            TrackPoint(31.140731, 118.636716),
            TrackPoint(31.140392, 118.635925),
            TrackPoint(31.140608, 118.635206),
        ),
    )

    fun generate(config: SimConfig): List<TrackPoint> {
        val baseRoute = campusRoutes.random(Random(config.startTimeMillis))
        val targetMeters = config.distanceKm * 1000.0
        val targetPointCount = max(2, (targetMeters / 40.0).toInt())
        val totalDurationMillis = config.durationMinutes * 60_000L
        val intervalMillis = if (targetPointCount <= 1) totalDurationMillis else {
            totalDurationMillis / (targetPointCount - 1)
        }

        val points = ArrayList<TrackPoint>(targetPointCount)
        var currentTime = config.startTimeMillis

        repeat(targetPointCount) { index ->
            val basePoint = baseRoute[index % baseRoute.size]
            points += jitter(basePoint).copy(timestampMillis = currentTime)
            currentTime += intervalMillis
        }

        return points
    }

    /** 基于用户录制的路线模板生成轨迹，委托给 PolylineSampler。 */
    fun generateFromTemplate(config: SimConfig, template: RouteTemplate): List<TrackPoint> =
        PolylineSampler.sampleFromTemplate(
            templatePoints = template.points,
            targetDistanceMeters = config.distanceKm * 1000.0,
            startTimeMillis = config.startTimeMillis,
            durationSeconds = config.durationMinutes * 60L,
        )

    private fun jitter(point: TrackPoint): TrackPoint {
        val latOffsetMeters = Random.nextDouble(-4.0, 4.0)
        val lngOffsetMeters = Random.nextDouble(-4.0, 4.0)
        val latOffset = latOffsetMeters / 111_320.0
        val lngOffset = lngOffsetMeters / (111_320.0 * cos(Math.toRadians(point.latitude)))
        return point.copy(
            latitude = point.latitude + latOffset,
            longitude = point.longitude + lngOffset,
        )
    }
}
