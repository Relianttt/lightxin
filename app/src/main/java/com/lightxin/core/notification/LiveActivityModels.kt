package com.lightxin.core.notification

import android.os.Bundle

/**
 * 通知点击后的路由目标。
 */
data class NotificationRoute(
    val destination: String,
    val extras: Bundle = Bundle.EMPTY,
)

/**
 * 展示形态：文本 或 进度条。
 */
sealed interface LiveActivityPresentation {
    data class Text(val subtitle: String? = null) : LiveActivityPresentation
    data class Progress(val current: Int, val total: Int, val label: String? = null) : LiveActivityPresentation
}

/**
 * 业务层构造的统一实况通知请求。
 * 不包含任何 Flyme / Android 16 私有字段。
 */
data class LiveActivityRequest(
    val key: String,
    val channelId: String,
    val title: String,
    val content: String,
    val capsuleText: String,
    val route: NotificationRoute,
    val ongoing: Boolean = true,
    val presentation: LiveActivityPresentation = LiveActivityPresentation.Text(),
    val extras: Bundle = Bundle.EMPTY,
)
