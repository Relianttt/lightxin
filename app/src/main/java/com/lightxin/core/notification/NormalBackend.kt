package com.lightxin.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lightxin.MainActivity
import com.lightxin.R

/**
 * 普通通知兜底 backend。
 * 不携带 Flyme 私有字段，不请求 promoted ongoing。
 */
class NormalBackend : LiveActivityBackend {

    companion object {
        const val CHANNEL_ID = "lightxin_live_normal"
    }

    override fun build(context: Context, request: LiveActivityRequest, notificationId: Int): Notification {
        ensureChannel(context)

        val pendingIntent = buildPendingIntent(context, request, notificationId)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_lightxin)
            .setContentTitle(request.title)
            .setContentText(request.content)
            .setOngoing(request.ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)

        when (val presentation = request.presentation) {
            is LiveActivityPresentation.Progress -> {
                builder.setProgress(presentation.total, presentation.current, false)
                presentation.label?.let { builder.setSubText(it) }
            }
            is LiveActivityPresentation.Text -> {
                presentation.subtitle?.let { builder.setSubText(it) }
            }
        }

        // BigTextStyle 展开显示详细信息
        request.extras.getString("bigText")?.let { bigText ->
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        return builder.build()
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
                "LightXin 通知",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "跑步、签到等正在进行的活动"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
    }
}
