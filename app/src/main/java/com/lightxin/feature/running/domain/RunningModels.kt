package com.lightxin.feature.running.domain

data class RunningDashboard(
    val todayKm: Double = 0.0,
    val completedKm: Double = 0.0,
    val leftKm: Double = 0.0,
    val taskTargetKm: Double = 0.0,
    val singleRunTargetKm: Double = 0.0,
    val maxKm: Double = 0.0,
    val maxKmDate: String = "",
    val dsFlag: Boolean = false,
    val studentTypeLabel: String = "",
)

data class RunningStartInfo(
    val exerciseId: String,
    val memberId: String,
    val runningType: String = "1",
    val mixOnceMileKm: Double = 1.0,
    val validSettingLj: String = "",
    val taskFlag: String = "",
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class RunningTrackerState(
    val startInfo: RunningStartInfo? = null,
    val startTimeMillis: Long = 0L,
    val isSessionActive: Boolean = false,
    val isCollecting: Boolean = false,
    val totalDistanceMeters: Double = 0.0,
    val points: List<TrackPoint> = emptyList(),
    val lastPoint: TrackPoint? = null,
    val locationLabel: String = "未开始",
    val errorMessage: String? = null,
)

data class RunningSnapshot(
    val startInfo: RunningStartInfo,
    val startTimeMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val points: List<TrackPoint>,
    val pointsAreBd09: Boolean = false,
)

data class SimConfig(
    val distanceKm: Double,
    val durationMinutes: Int,
    val startTimeMillis: Long,
)

data class RunningResult(
    val success: Boolean,
    val message: String,
    val uploadId: String = "",
    val startDate: String,
    val endDate: String,
    val distanceKm: Double,
    val durationSeconds: Long,
    val speedKmh: Double,
    val pointCount: Int,
)
