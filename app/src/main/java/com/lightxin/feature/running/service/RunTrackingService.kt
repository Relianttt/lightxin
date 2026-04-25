package com.lightxin.feature.running.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lightxin.R
import com.lightxin.core.location.LocationProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RunTrackingService : Service() {

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var tracker: RunningTracker

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
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification("跑步进行中", "正在等待 GPS"))

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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyState() {
        val state = tracker.state.value
        val distanceKm = state.totalDistanceMeters / 1000.0
        val title = if (state.isCollecting) "跑步进行中" else "跑步已暂停"
        val content = if (state.points.isEmpty()) {
            state.locationLabel
        } else {
            String.format("已记录 %.2f km，%s", distanceKm, state.locationLabel)
        }
        notificationManager().notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun buildNotification(title: String, content: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "LightXin 跑步跟踪",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "lightxin_running"
        private const val NOTIFICATION_ID = 2001
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
