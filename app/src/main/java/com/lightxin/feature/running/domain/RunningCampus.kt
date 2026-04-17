package com.lightxin.feature.running.domain

import kotlin.math.cos

object RunningCampus {

    const val CENTER_LATITUDE = 31.13893
    const val CENTER_LONGITUDE = 118.628131

    private const val METERS_PER_LAT_DEGREE = 111_320.0
    private const val CAMPUS_HALF_HEIGHT_METERS = 600.0
    private const val CAMPUS_HALF_WIDTH_METERS = 600.0

    val minLatitude: Double = CENTER_LATITUDE - metersToLatitude(CAMPUS_HALF_HEIGHT_METERS)
    val maxLatitude: Double = CENTER_LATITUDE + metersToLatitude(CAMPUS_HALF_HEIGHT_METERS)
    val minLongitude: Double = CENTER_LONGITUDE - metersToLongitude(CAMPUS_HALF_WIDTH_METERS)
    val maxLongitude: Double = CENTER_LONGITUDE + metersToLongitude(CAMPUS_HALF_WIDTH_METERS)

    fun pointAt(latOffsetMeters: Double, lngOffsetMeters: Double): TrackPoint {
        return TrackPoint(
            latitude = CENTER_LATITUDE + metersToLatitude(latOffsetMeters),
            longitude = CENTER_LONGITUDE + metersToLongitude(lngOffsetMeters),
        )
    }

    private fun metersToLatitude(meters: Double): Double = meters / METERS_PER_LAT_DEGREE

    private fun metersToLongitude(meters: Double): Double {
        return meters / (METERS_PER_LAT_DEGREE * cos(Math.toRadians(CENTER_LATITUDE)))
    }
}
