package com.lightxin

import com.lightxin.feature.running.domain.RouteQualityStatus
import com.lightxin.feature.running.domain.RouteTemplate
import com.lightxin.feature.running.domain.RouteTemplateSource
import com.lightxin.feature.running.domain.TrackPoint

object TestFixtures {

    /** 35 个点、每点约 55m，总长约 1.9km，能过模板硬闸。 */
    fun validPoints(count: Int = 35): List<TrackPoint> = (0 until count).map { index ->
        TrackPoint(
            latitude = 31.1357 + index * 0.0005,
            longitude = 118.6265 + index * 0.00005,
            timestampMillis = 1_700_000_000_000L + index * 5_000L,
        )
    }

    fun routeTemplate(
        id: String = "template-1",
        name: String = "操场环线",
        isDefault: Boolean = false,
        createdAtMillis: Long = 1_700_000_000_000L,
        source: RouteTemplateSource = RouteTemplateSource.TEMPLATE_RECORDING,
        points: List<TrackPoint> = validPoints(),
    ): RouteTemplate = RouteTemplate(
        id = id,
        name = name,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = createdAtMillis,
        totalDistanceMeters = 1_900.0,
        durationSeconds = 175L,
        source = source,
        isDefault = isDefault,
        points = points,
        qualityStatus = RouteQualityStatus.PASS,
        qualityMessage = null,
    )
}
