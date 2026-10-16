package com.lightxin.feature.running.domain

object TrajectoryGenerator {
    fun generateFromTemplate(config: SimConfig, template: RouteTemplate): List<TrackPoint> =
        PolylineSampler.sampleFromTemplate(
            templatePoints = template.points,
            targetDistanceMeters = config.distanceKm * 1000.0,
            startTimeMillis = config.startTimeMillis,
            durationSeconds = config.durationMinutes * 60L,
        )
}
