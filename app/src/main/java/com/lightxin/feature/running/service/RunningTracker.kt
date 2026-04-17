package com.lightxin.feature.running.service

import android.location.Location
import com.lightxin.feature.running.domain.RunningSnapshot
import com.lightxin.feature.running.domain.RunningStartInfo
import com.lightxin.feature.running.domain.RunningTrackerState
import com.lightxin.feature.running.domain.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
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
            locationLabel = "正在获取 GPS",
            errorMessage = null,
        )
    }

    fun onLocationUpdate(location: Location) {
        val current = _state.value
        if (!current.isSessionActive) return

        val point = TrackPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )

        val previous = current.lastPoint
        val segmentMeters = if (previous == null) {
            0.0
        } else {
            haversineMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
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

    fun stopCollecting() {
        _state.value = _state.value.copy(
            isCollecting = false,
            locationLabel = if (_state.value.points.isEmpty()) "已停止" else "轨迹已锁定",
        )
    }

    fun snapshotForUpload(): RunningSnapshot? {
        val current = _state.value
        val startInfo = current.startInfo ?: return null
        val durationSeconds = ((System.currentTimeMillis() - current.startTimeMillis) / 1000L).coerceAtLeast(1L)
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

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))
        return earthRadius * c
    }
}
