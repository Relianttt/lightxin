package com.lightxin.core.notification

import android.app.Notification
import android.content.Context

/**
 * 各平台 backend 的统一接口。
 */
interface LiveActivityBackend {
    fun build(context: Context, request: LiveActivityRequest, notificationId: Int): Notification
}
