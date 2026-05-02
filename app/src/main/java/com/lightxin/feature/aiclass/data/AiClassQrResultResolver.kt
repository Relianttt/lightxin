package com.lightxin.feature.aiclass.data

internal object AiClassQrResultResolver {
    fun requiresSessionRefresh(result: QrRequestResult): Boolean =
        result.code == 302 && result.location.contains("login", ignoreCase = true)

    fun resolveResult(result: QrRequestResult): Result<String> {
        return if (result.code == 302 && result.location.contains("codeExpired", ignoreCase = true)) {
            Result.failure(Exception("二维码已过期"))
        } else if (result.code == 302 && result.location.contains("signSuccess", ignoreCase = true)) {
            Result.success("扫码签到成功")
        } else if (result.code == 302) {
            Result.failure(Exception(resolveRedirectMessage(result.location)))
        } else {
            Result.failure(Exception("签到失败（HTTP ${result.code}）"))
        }
    }

    private fun resolveRedirectMessage(location: String): String {
        if (location.isBlank()) return "扫码签到失败：服务端未返回跳转地址"
        return when {
            location.contains("loginManage", ignoreCase = true) -> "扫码签到失败：AI课堂登录态已失效"
            location.contains("login", ignoreCase = true) -> "扫码签到失败：AI课堂登录态已失效"
            location.contains("course", ignoreCase = true) -> "扫码签到失败：二维码跳转到了课程页"
            else -> "扫码签到失败：未识别的跳转结果"
        }
    }
}

internal data class QrRequestResult(
    val code: Int,
    val location: String,
    val requestUrl: String,
)
