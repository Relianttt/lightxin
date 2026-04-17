package com.lightxin.feature.running.domain

import kotlin.math.cos
import kotlin.math.max
import kotlin.random.Random

object TrajectoryGenerator {

    private val campusRoutes = listOf(
        listOf(
            RunningCampus.pointAt(150.0, 210.0),
            RunningCampus.pointAt(230.0, 110.0),
            RunningCampus.pointAt(250.0, -30.0),
            RunningCampus.pointAt(170.0, -180.0),
            RunningCampus.pointAt(20.0, -220.0),
            RunningCampus.pointAt(-130.0, -140.0),
            RunningCampus.pointAt(-180.0, 10.0),
            RunningCampus.pointAt(-90.0, 190.0),
        ),
        listOf(
            RunningCampus.pointAt(60.0, -230.0),
            RunningCampus.pointAt(210.0, -170.0),
            RunningCampus.pointAt(280.0, -20.0),
            RunningCampus.pointAt(240.0, 160.0),
            RunningCampus.pointAt(100.0, 240.0),
            RunningCampus.pointAt(-70.0, 210.0),
            RunningCampus.pointAt(-190.0, 60.0),
            RunningCampus.pointAt(-140.0, -140.0),
        ),
        listOf(
            RunningCampus.pointAt(190.0, -80.0),
            RunningCampus.pointAt(260.0, 40.0),
            RunningCampus.pointAt(220.0, 200.0),
            RunningCampus.pointAt(70.0, 250.0),
            RunningCampus.pointAt(-80.0, 180.0),
            RunningCampus.pointAt(-200.0, 40.0),
            RunningCampus.pointAt(-170.0, -140.0),
            RunningCampus.pointAt(-10.0, -210.0),
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
