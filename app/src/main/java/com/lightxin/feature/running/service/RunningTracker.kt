package com.lightxin.feature.running.service

import android.location.Location
import com.lightxin.feature.running.domain.GeoDistance
import com.lightxin.feature.running.domain.RunningSnapshot
import com.lightxin.feature.running.domain.RunningStartInfo
import com.lightxin.feature.running.domain.RunningTrackerState
import com.lightxin.feature.running.domain.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunningTracker @Inject constructor() {

    private val _state = MutableStateFlow(RunningTrackerState())
    val state: StateFlow<RunningTrackerState> = _state

    fun beginSession(startInfo: RunningStartInfo, startTimeMillis: Long = System.currentTimeMillis()) {
        _state.value = RunningTrackerState(
            startInfo = startInfo,
            startTimeMillis = startTimeMillis,
            isSessionActive = true,
            isCollecting = true,
            locationLabel = "等待定位",
        )
    }

    /** 开启模板录制会话：不涉及跑步 startInfo。 */
    fun beginTemplateSession(startTimeMillis: Long = System.currentTimeMillis()) {
        _state.value = RunningTrackerState(
            startInfo = null,
            startTimeMillis = startTimeMillis,
            isSessionActive = true,
            isCollecting = true,
            locationLabel = "等待定位",
        )
    }

    fun onServiceStarted() {
        _state.value = _state.value.copy(
            isCollecting = true,
            endTimeMillis = null,
            locationLabel = "正在获取 GPS",
            errorMessage = null,
        )
    }

    fun onLocationUpdate(location: Location) {
        val current = _state.value
        if (!current.isSessionActive) return
        // 已锁定（结束/失败待重试）就不再收点，避免重试上传的轨迹悄悄变长
        if (current.endTimeMillis != null) return

        val point = TrackPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )

        val previous = current.lastPoint
        val segmentMeters = if (previous == null) {
            0.0
        } else {
            GeoDistance.metersBetween(previous.latitude, previous.longitude, point.latitude, point.longitude)
        }

        val acceptedSegment = when {
            previous == null -> 0.0
            segmentMeters < 1.5 -> 0.0
            segmentMeters > 120.0 -> 0.0
            else -> segmentMeters
        }

        val nextPoints = if (previous == null || acceptedSegment > 0.0) {
            current.points + point
        } else {
            current.points
        }

        _state.value = current.copy(
            totalDistanceMeters = current.totalDistanceMeters + acceptedSegment,
            points = nextPoints,
            lastPoint = point,
            locationLabel = if (nextPoints.size >= 2) "定位正常" else "已获取定位",
            errorMessage = null,
        )
    }

    fun onLocationError(message: String) {
        _state.value = _state.value.copy(
            isCollecting = false,
            locationLabel = "定位失败",
            errorMessage = message,
        )
    }

    /**
     * 锁定轨迹与结束时间。
     *
     * 时长必须在这里定稿：上传失败后会保留会话让用户原地重试，若快照每次现算时长，
     * 等待重试的几分钟会被算进配速，两次上报的 time/speed 也会自相矛盾。
     */
    fun stopCollecting() {
        val current = _state.value
        _state.value = current.copy(
            isCollecting = false,
            endTimeMillis = current.endTimeMillis ?: System.currentTimeMillis(),
            locationLabel = if (current.points.isEmpty()) "已停止" else "轨迹已锁定",
        )
    }

    fun snapshotForUpload(): RunningSnapshot? {
        val current = _state.value
        val startInfo = current.startInfo ?: return null
        val endTimeMillis = current.endTimeMillis ?: System.currentTimeMillis()
        val durationSeconds = ((endTimeMillis - current.startTimeMillis) / 1000L).coerceAtLeast(1L)
        return RunningSnapshot(
            startInfo = startInfo,
            startTimeMillis = current.startTimeMillis,
            durationSeconds = durationSeconds,
            distanceMeters = current.totalDistanceMeters,
            points = current.points.ifEmpty { listOfNotNull(current.lastPoint) },
        )
    }

    fun completeSession() {
        _state.value = RunningTrackerState()
    }

    fun cancelSession() {
        _state.value = RunningTrackerState()
    }
}
