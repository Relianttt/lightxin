package com.lightxin.core.network

import java.io.IOException
import java.net.SocketTimeoutException

enum class FifMappingReason {
    NETWORK, TIMEOUT, SERVER, ACCOUNT_INFO, UNKNOWN,
}

class FifUserMappingException(
    val reason: FifMappingReason = FifMappingReason.UNKNOWN,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception("FIF 用户映射失败", cause)

data class FifMappingFailure(
    val reason: FifMappingReason,
    val operation: FifOperation,
    val httpStatus: Int? = null,
) {
    val message: String get() = when (reason) {
        FifMappingReason.NETWORK -> "当前无法连接 AI 课堂，请检查网络后重试。"
        FifMappingReason.TIMEOUT -> "连接 AI 课堂超时，请稍后重试。"
        FifMappingReason.SERVER -> "AI 课堂服务暂时异常，请稍后重试。"
        FifMappingReason.ACCOUNT_INFO -> "未能获取你的 AI 课堂账号信息，请重新尝试连接。"
        FifMappingReason.UNKNOWN -> "暂时无法完成 AI 课堂连接，请重试或查看错误详情。"
    }

    // Only structured, locally defined values enter the user-visible diagnostic.
    val technicalDetail: String get() = buildString {
        append("FIF_MAPPING_FAILED\n操作：${operation.label}\n阶段：getAiktUserIdByMemberId\n原因：${reason.name}")
        httpStatus?.takeIf { it in 100..599 }?.let { append("\nHTTP $it") }
    }
}

enum class FifOperation(val label: String) {
    HOME("加载课堂"), QUIZ("加载测验"), HOMEWORK("加载作业"),
    SIGN_CODE("数字码签到"), QR_SIGN("扫码签到"),
    HOMEWORK_DETAIL("加载作业详情"), WORKS_MORE("加载更多提交"), SUBMIT_HOMEWORK("提交作业"),
}

fun Throwable.fifMappingFailure(operation: FifOperation): FifMappingFailure? {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    var legacy = false
    while (current != null && seen.add(current)) {
        if (current is FifUserMappingException) {
            return FifMappingFailure(current.reason, operation, current.httpStatus)
        }
        legacy = legacy || current.message?.contains("FIF 用户映射失败") == true
        current = current.cause
    }
    return if (legacy) FifMappingFailure(FifMappingReason.UNKNOWN, operation) else null
}

internal fun mappingRequestFailure(error: Exception): FifUserMappingException =
    if (error is FifUserMappingException) error else FifUserMappingException(
        reason = when (error) {
            is SocketTimeoutException -> FifMappingReason.TIMEOUT
            is IOException -> FifMappingReason.NETWORK
            else -> FifMappingReason.UNKNOWN
        },
        cause = error,
    )
