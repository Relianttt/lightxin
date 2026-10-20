package com.lightxin.feature.aiclass.data

import android.util.Log
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.network.FifMappingReason
import com.lightxin.core.network.FifSessionManager
import com.lightxin.core.network.FifUserMappingException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val PROBE_LOG_TAG = "AiClassProbe"

/**
 * 会话体检结论。**只有 [Blocked] 与 [LoginExpired] 该在首页提示**，其余三种都必须沉默：
 * [Skipped] 是没用过 AI课堂，[Inconclusive] 是断网 / 超时 / 服务端异常 —— 把断网说成
 * “你被人顶号了”，用户会去做一次毫无意义的重新登录，还要把官方 App 顶下线。
 */
enum class AiClassSessionCheck { Skipped, Healthy, Blocked, LoginExpired, Inconclusive }

/**
 * AI课堂会话体检。
 *
 * 为什么需要主动探测：FIF 的 token 与 studentId 持久化在 DataStore，SESSION Cookie 却只在内存，
 * 所以进程活着的时候 `isSessionValid()` 恒为 true。官方 App 在别处登录把服务端映射顶掉之后，
 * 本地完全察觉不到，要等到扫码那一刻才被服务端打回。这里主动跑一次 SSO：
 * 成功即顺带把会话续上，失败才说明服务端已经不认这个映射了。
 *
 * 结论与取舍见 `.agents/notes/2026-10-17-decision-login-expiry-surfaces.md`。
 */
@Singleton
class AiClassSessionGuard @Inject constructor(
    private val fifSession: FifSessionManager,
    private val tokenManager: TokenManager,
) {
    suspend fun checkOnLaunch(): AiClassSessionCheck {
        val outcome = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
            // 判据是“用过 AI课堂”，不是“有 FIF token”：被顶号时 SSO 失败、token 不会落盘，
            // 用 token 当判据会把“用过但正被顶”误判成“从没用过”，横幅永远不会亮。
            if (!fifSession.isFifUsed()) {
                return@withTimeoutOrNull AiClassSessionCheck.Skipped to null
            }
            if (tokenManager.getAccessToken().isNullOrBlank()) {
                return@withTimeoutOrNull AiClassSessionCheck.LoginExpired to null
            }
            val error = fifSession.probeSession().exceptionOrNull()
            if (error == null) {
                AiClassSessionCheck.Healthy to null
            } else {
                classifyProbeFailure(error) to error
            }
        } ?: (AiClassSessionCheck.Inconclusive to null)

        val (conclusion, error) = outcome
        val cause = rootCauseChain(error).joinToString(" <- ") { when (it) {
            is FifUserMappingException -> "FifUserMappingException(${it.reason}, HTTP ${it.httpStatus})"
            else -> it::class.simpleName.orEmpty()
        } }
        val line = "会话体检：$conclusion" + if (cause.isBlank()) "" else "；原因链：$cause"
        // 结论一律留痕：横幅误报/漏报时，第一手依据只能是这条日志
        if (conclusion == AiClassSessionCheck.Healthy || conclusion == AiClassSessionCheck.Skipped) {
            Log.i(PROBE_LOG_TAG, line)
        } else {
            Log.w(PROBE_LOG_TAG, line)
        }
        return conclusion
    }

    /** 重登前丢弃本地 FIF 身份，避免下次 SSO 带着旧映射重来 */
    suspend fun clearFifSession() {
        fifSession.clear()
    }

    private companion object {
        const val CHECK_TIMEOUT_MS = 12_000L
    }
}

/**
 * 失败归因。只有“服务端明确不认这个身份”才算被顶号：
 *
 * - `ACCOUNT_INFO`：映射接口返回了结构但没有用户标识 —— 服务端已经把这个账号重新映射走了；
 * - 全新 token 收到 401 / 403：同样是服务端拒绝这个身份。
 *
 * 其余（网络、超时、5xx、解析不出的 `UNKNOWN`）一律 `Inconclusive` —— 不能拿它当证据。
 */
internal fun classifyProbeFailure(error: Throwable?): AiClassSessionCheck {
    val mapping = rootCauseChain(error).filterIsInstance<FifUserMappingException>().firstOrNull()
    if (mapping == null) {
        // 网络层异常本身已经由 FifSessionManager 包过一层，这里只看有没有明确的服务端拒绝
        return AiClassSessionCheck.Inconclusive
    }
    return when {
        mapping.reason == FifMappingReason.ACCOUNT_INFO -> AiClassSessionCheck.Blocked
        mapping.httpStatus == 401 || mapping.httpStatus == 403 -> AiClassSessionCheck.Blocked
        else -> AiClassSessionCheck.Inconclusive
    }
}

/** 沿 cause 链展开，带环保护（异常可能自引用或被复用）。 */
internal fun rootCauseChain(error: Throwable?): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    val seen = mutableSetOf<Throwable>()
    var current = error
    while (current != null && seen.add(current)) {
        chain += current
        current = current.cause
    }
    return chain
}
