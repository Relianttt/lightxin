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
    val clubSummary: ClubSummary? = null,
    /** 服务端下发的有效速度区间（km/h）；null 表示该字段未下发。 */
    val minSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
)

/** 大一大二首页俱乐部摘要，来自 index/clubInfo.do；大三大四为 null。 */
data class ClubSummary(
    val courseName: String = "",
    val term: String = "",
    val teacherName: String = "",
    val memberLevel: String = "",
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
    val endTimeMillis: Long? = null,
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

/**
 * 服务端对一条跑步记录的判定回读。`state == "1"` 为有效，`"0"` / `"2"` 为无效，
 * 原因在 `remark`。`runningRoute` 在服务端是加密存储的，所以只能判断有没有存下。
 */
data class RunRecordVerdict(
    val checkId: String,
    val state: String,
    val remark: String,
    val hasStoredRoute: Boolean,
) {
    val isValid: Boolean get() = state == "1"

    fun describe(): String = when {
        isValid -> "有效"
        remark.isNotBlank() -> "无效：$remark"
        else -> "无效（state=$state）"
    }
}

data class SimConfig(
    val distanceKm: Double,
    val durationMinutes: Int,
    val startTimeMillis: Long,
)

/**
 * 生成好的模拟轨迹，自带坐标系标记。
 * 上传方据此决定是否做 WGS-84 → BD-09 转换，避免在调用处各自猜测。
 */
data class GeneratedTrajectory(
    val points: List<TrackPoint>,
    val isBd09: Boolean,
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
    /** 服务端今日里程在本次上传前后的增量；null 表示无法判定。为 0 即本次未计入里程。 */
    val creditedKm: Double? = null,
)
