package com.lightxin.feature.aiclass.data

import com.lightxin.core.network.FifMappingReason
import com.lightxin.core.network.FifUserMappingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 体检结论只有一格允许上首页提示，其余都必须沉默。
 *
 * 误报的代价高于漏报：把断网说成"账号已在其他设备登录"，用户会做一次毫无意义的重新登录，
 * 还要把官方 App 顶下线。所以这里把每一种失败形态都钉住。
 */
class AiClassSessionGuardTest {

    @Test
    fun `rejected mapping is the only blocked signal`() {
        assertEquals(
            AiClassSessionCheck.Blocked,
            classifyProbeFailure(FifUserMappingException(FifMappingReason.ACCOUNT_INFO)),
        )
        // 被 FifSessionManager 包过一层也要能认出来
        assertEquals(
            AiClassSessionCheck.Blocked,
            classifyProbeFailure(
                Exception("AI课堂会话体检失败: FIF 用户映射失败", FifUserMappingException(FifMappingReason.ACCOUNT_INFO)),
            ),
        )
    }

    @Test
    fun `unauthorized response on a fresh token means blocked`() {
        assertEquals(
            AiClassSessionCheck.Blocked,
            classifyProbeFailure(FifUserMappingException(FifMappingReason.SERVER, httpStatus = 401)),
        )
        assertEquals(
            AiClassSessionCheck.Blocked,
            classifyProbeFailure(FifUserMappingException(FifMappingReason.UNKNOWN, httpStatus = 403)),
        )
    }

    @Test
    fun `network failures never become a device conflict`() {
        val inconclusive = listOf(
            FifUserMappingException(FifMappingReason.NETWORK, cause = IOException("Unable to resolve host")),
            FifUserMappingException(FifMappingReason.TIMEOUT),
            FifUserMappingException(FifMappingReason.SERVER, httpStatus = 503),
            FifUserMappingException(FifMappingReason.UNKNOWN),
            IOException("Unable to resolve host"),
            Exception("AI课堂会话体检失败: timeout"),
            null,
        )
        inconclusive.forEach { error ->
            assertEquals(
                "不该把 ${error?.javaClass?.simpleName} 判成被顶号",
                AiClassSessionCheck.Inconclusive,
                classifyProbeFailure(error),
            )
        }
    }

    @Test
    fun `cause chain walk terminates on a circular cause`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        // JDK 禁止 initCause(this)，但两跳环是能构造的：a -> b -> a
        a.initCause(b)

        assertEquals(listOf<Throwable>(a, b), rootCauseChain(a))
        // 环也不能把分类拖进死循环
        assertEquals(AiClassSessionCheck.Inconclusive, classifyProbeFailure(a))
    }
}
