package com.lightxin.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.RemoteViews
import com.lightxin.MainActivity
import com.lightxin.R

/**
 * Flyme 私有实况通知 backend。
 * 使用 notification.live.* extras 实现胶囊，展开卡片用 RemoteViews。
 */
class FlymeLiveBackend : LiveActivityBackend {

    companion object {
        const val CHANNEL_ID = "lightxin_live_flyme"
        private const val CAPSULE_BG_COLOR = 0xFF4CAF50.toInt()
        private const val CAPSULE_CONTENT_COLOR = 0xFFFFFFFF.toInt()
    }

    override fun build(context: Context, request: LiveActivityRequest, notificationId: Int): Notification {
        ensureChannel(context)

        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 3)
            putString("notification.live.capsuleContent", request.capsuleText)
            putParcelable(
                "notification.live.capsuleIcon",
                Icon.createWithResource(context, R.mipmap.ic_launcher),
            )
            putInt("notification.live.capsuleBgColor", CAPSULE_BG_COLOR)
            putInt("notification.live.capsuleContentColor", CAPSULE_CONTENT_COLOR)
        }

        val liveBundle = Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", 0)
            putInt("notification.live.type", 10)
            putBundle("notification.live.capsule", capsuleBundle)
            putInt("notification.live.contentColor", CAPSULE_CONTENT_COLOR)
        }

        val contentRemoteViews = buildRemoteViews(context, request)
        val pendingIntent = buildPendingIntent(context, request, notificationId)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_lightxin)
            .setContentTitle(request.title)
            .setContentText(request.content)
            .setContentIntent(pendingIntent)
            .addExtras(liveBundle)
            .setCustomContentView(contentRemoteViews)
            .setAutoCancel(false)
            .setOngoing(request.ongoing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        return notification
    }

    private fun buildRemoteViews(context: Context, request: LiveActivityRequest): RemoteViews {
        val extras = request.extras

        // 课程通知使用专用布局
        if (extras.getString("courseName") != null) {
            val views = RemoteViews(context.packageName, R.layout.notification_live_course)
            views.setTextViewText(R.id.live_course_name, extras.getString("courseName"))
            views.setTextViewText(R.id.live_course_status, extras.getString("statusText"))
            views.setTextViewText(R.id.live_course_room, extras.getString("room"))
            views.setImageViewResource(R.id.live_course_icon, R.mipmap.ic_launcher)
            return views
        }

        // 跑步通知布局
        val views = RemoteViews(context.packageName, R.layout.notification_live_running)
        views.setTextViewText(R.id.live_title, request.title)
        views.setTextViewText(R.id.live_distance, extras.getString("distance") ?: "0.00 km")
        views.setTextViewText(R.id.live_line2, extras.getString("line2") ?: "")
        views.setImageViewResource(R.id.live_icon, R.mipmap.ic_launcher)
        return views
    }

    private fun buildPendingIntent(context: Context, request: LiveActivityRequest, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("notification_route", request.route.destination)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "LightXin 实况通知",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "跑步、签到等正在进行的活动"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
    }
}
