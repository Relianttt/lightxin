package com.lightxin.core.notification

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri

/**
 * 检测当前设备支持的实况通知能力。
 */
object DeviceCapability {

    enum class Backend { FLYME, ANDROID16, NORMAL }

    fun resolve(context: Context): Backend = when {
        isFlymeEnabled(context) -> Backend.FLYME
        isAndroid16Promoted(context) -> Backend.ANDROID16
        else -> Backend.NORMAL
    }

    private fun isFlymeEnabled(context: Context): Boolean {
        if (!Build.MANUFACTURER.equals("meizu", ignoreCase = true)) return false
        if (getFlymeVersion() < 11) return false
        if (context.checkSelfPermission("flyme.permission.READ_NOTIFICATION_LIVE_STATE")
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val result = context.contentResolver.call(
                "content://com.android.systemui.notification.provider".toUri(),
                "isNotificationLiveEnabled",
                null,
                null,
            )
            result?.getBoolean("result", false) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun getFlymeVersion(): Int {
        val display = Build.DISPLAY ?: return -1
        val match = Regex("Flyme\\s*([0-9]+)").find(display)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    private fun isAndroid16Promoted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.canPostPromotedNotifications()
        } catch (_: Exception) {
            false
        }
    }
}
