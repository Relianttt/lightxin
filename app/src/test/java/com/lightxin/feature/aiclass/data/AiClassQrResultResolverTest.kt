package com.lightxin.feature.aiclass.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClassQrResultResolverTest {

    @Test
    fun `resolve sign success redirect`() {
        val result = AiClassQrResultResolver.resolveResult(
            qrResult(location = "https://sttp.fifedu.com/signSuccess"),
        )

        assertTrue(result.isSuccess)
        assertEquals("扫码签到成功", result.getOrThrow())
    }

    @Test
    fun `resolve expired qr redirect`() {
        val result = AiClassQrResultResolver.resolveResult(
            qrResult(location = "https://sttp.fifedu.com/codeExpired"),
        )

        assertTrue(result.isFailure)
        assertEquals("二维码已过期", result.exceptionOrNull()?.message)
    }

    @Test
    fun `login redirect requires session refresh and reports expired session`() {
        val requestResult = qrResult(location = "https://sttp.fifedu.com/loginManage")

        val result = AiClassQrResultResolver.resolveResult(requestResult)

        assertTrue(AiClassQrResultResolver.requiresSessionRefresh(requestResult))
        assertTrue(result.isFailure)
        assertEquals("扫码签到失败：AI课堂登录态已失效", result.exceptionOrNull()?.message)
    }

    @Test
    fun `non redirect reports http failure and does not refresh session`() {
        val requestResult = qrResult(code = 500, location = "")

        val result = AiClassQrResultResolver.resolveResult(requestResult)

        assertFalse(AiClassQrResultResolver.requiresSessionRefresh(requestResult))
        assertTrue(result.isFailure)
        assertEquals("签到失败（HTTP 500）", result.exceptionOrNull()?.message)
    }

    private fun qrResult(
        code: Int = 302,
        location: String,
    ): QrRequestResult {
        return QrRequestResult(
            code = code,
            location = location,
            requestUrl = "https://sttp.fifedu.com/coursecenter-interaction/qrcodeV2/qrcodeHandler",
        )
    }
}
