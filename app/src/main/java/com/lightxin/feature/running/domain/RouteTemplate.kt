package com.lightxin.feature.running.domain

data class RouteTemplate(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val totalDistanceMeters: Double,
    val durationSeconds: Long,
    val source: RouteTemplateSource,
    val isDefault: Boolean,
    val points: List<TrackPoint>,
) {
    val pointCount: Int get() = points.size
}

enum class RouteTemplateSource {
    TEMPLATE_RECORDING,
}

sealed class RouteTemplateSaveResult {
    data class Success(val template: RouteTemplate) : RouteTemplateSaveResult()
    data class Rejected(val reason: String) : RouteTemplateSaveResult()
}

object RouteTemplateRules {
    const val MIN_POINTS = 20
    const val MIN_DISTANCE_METERS = 1_000.0

    fun validate(pointCount: Int, distanceMeters: Double): String? = when {
        pointCount <= MIN_POINTS -> "轨迹点需大于 $MIN_POINTS 个，当前 $pointCount"
        distanceMeters <= MIN_DISTANCE_METERS ->
            "距离需大于 ${(MIN_DISTANCE_METERS / 1000).toInt()} km，当前 %.2f km".format(distanceMeters / 1000.0)
        else -> null
    }
}
