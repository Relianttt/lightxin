package com.lightxin.feature.running.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.lightxin.core.location.LocationProvider
import com.lightxin.core.notification.LiveActivityNotifier
import com.lightxin.core.notification.LiveActivityRequest
import com.lightxin.core.notification.NotificationRoute
import com.lightxin.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class RunTrackingService : Service() {

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var tracker: RunningTracker
    @Inject lateinit var notifier: LiveActivityNotifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking(removeSession = false)
            ACTION_CANCEL -> stopTracking(removeSession = true)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTracking() {
        if (!hasLocationPermission()) {
            tracker.onLocationError("缺少定位权限")
            stopSelf()
            return
        }

        val initialRequest = LiveActivityRequest(
            key = NOTIFICATION_KEY,
            channelId = NOTIFICATION_KEY,
            title = "跑步进行中",
            content = "正在等待 GPS",
            capsuleText = "0.00km",
            route = NotificationRoute(destination = Routes.RUNNING_ACTIVE),
            extras = Bundle().apply {
                putString("bigText", "正在等待 GPS")
                putString("distance", "0.00 km")
                putString("line2", "等待定位中...")
                putString("line3", "")
            },
        )
        startForeground(notifier.idFor(initialRequest), notifier.buildInitial(initialRequest))

        trackingJob?.cancel()
        tracker.onServiceStarted()
        trackingJob = serviceScope.launch {
            try {
                locationProvider.locationUpdates(intervalMs = 3000L, minDistanceM = 2f).collect { location ->
                    tracker.onLocationUpdate(location)
                    notifyState()
                }
            } catch (_: SecurityException) {
                tracker.onLocationError("缺少定位权限")
                stopTracking(removeSession = false)
            }
        }
    }

    private fun stopTracking(removeSession: Boolean) {
        trackingJob?.cancel()
        trackingJob = null
        if (removeSession) {
            tracker.cancelSession()
        } else {
            tracker.stopCollecting()
        }
        notifier.cancel(NOTIFICATION_KEY)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyState() {
        val state = tracker.state.value
        val distanceKm = state.totalDistanceMeters / 1000.0
        val durationSeconds = ((System.currentTimeMillis() - state.startTimeMillis) / 1000L).coerceAtLeast(0L)
        val speedKmh = if (durationSeconds > 0L) distanceKm / durationSeconds * 3600.0 else 0.0

        val title = if (state.isCollecting) "跑步进行中" else "跑步已暂停"
        val distanceStr = String.format("%.2f km", distanceKm)
        val durationStr = formatDuration(durationSeconds)
        val speedStr = if (speedKmh > 0.0) String.format("%.1f km/h", speedKmh) else "-- km/h"
        val capsuleText = String.format("%.2fkm", distanceKm)
        val content = "距离 $distanceStr · 时长 $durationStr"
        val bigText = "距离 $distanceStr · 时长 $durationStr\n速度 $speedStr\nGPS ${state.locationLabel}"

        val extras = Bundle().apply {
            putString("bigText", bigText)
            putString("distance", distanceStr)
            putString("line2", "时长 $durationStr | 速度 $speedStr | ${state.locationLabel}")
            putString("line3", "")
        }

        val request = LiveActivityRequest(
            key = NOTIFICATION_KEY,
            channelId = NOTIFICATION_KEY,
            title = title,
            content = content,
            capsuleText = capsuleText,
            route = NotificationRoute(destination = Routes.RUNNING_ACTIVE),
            extras = extras,
        )
        notifier.show(request)
    }

    private fun formatDuration(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    companion object {
        private const val NOTIFICATION_KEY = "running"
        private const val ACTION_START = "com.lightxin.running.START"
        private const val ACTION_STOP = "com.lightxin.running.STOP"
        private const val ACTION_CANCEL = "com.lightxin.running.CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(ACTION_CANCEL)
            context.startService(intent)
        }
    }
}
