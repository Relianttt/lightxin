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
    val isSessionActive: Boolean = false,
    val isCollecting: Boolean = false,
    val totalDistanceMeters: Double = 0.0,
    val points: List<TrackPoint> = emptyList(),
    val lastPoint: TrackPoint? = null,
    /** `stopCollecting()` 时锁定；非 null 表示轨迹与时长都已定稿，之后不再接受新点。 */
    val endTimeMillis: Long? = null,
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
    /** 总时长（秒）。允许到秒，所以不再用整分钟表示。 */
    val durationSeconds: Long,
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
    /** 回读到的服务端判定文案；未回读或回读失败时为空，界面不显示该行。 */
    val verdict: String = "",
    /** 上传前后 todayKm 的差值；null 表示无法判定（未取到首页数据）。0 表示服务端没计入。 */
    val creditedKm: Double? = null,
)

/**
 * 服务端对单条跑步记录的判定，来自 `mobile/extra/getExtraCheck.do`。
 *
 * `addExtraCheckNew.do` 返回的 `flag/result/id` 只代表记录建了；是否算有效只有这里知道。
 */
data class RunRecordVerdict(
    val checkId: String = "",
    val state: String = "",
    val remark: String = "",
    /** 轨迹有没有存下来。实测服务端以密文存储，只能判断有无，不解析点数。 */
    val hasStoredRoute: Boolean = false,
) {
    val isValid: Boolean get() = state == "1"

    fun describe(): String = when {
        isValid -> "有效"
        remark.isNotBlank() -> "无效：$remark"
        else -> "无效（state=$state）"
    }

    /** 结果页一行文案；是否显示由调用方按空白判断。 */
    fun summary(): String = "${describe()}；轨迹已存：${if (hasStoredRoute) "是" else "否"}"
}
