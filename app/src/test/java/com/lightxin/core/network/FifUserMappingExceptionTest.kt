package com.lightxin.core.network

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.io.IOException

class FifUserMappingExceptionTest {
    @Test fun findsTypedFailureThroughWrappersWithoutLeakingMessages() {
        val error = Exception("token=secret&userCode=private", FifUserMappingException(FifMappingReason.SERVER, 503))
        val failure = error.fifMappingFailure(FifOperation.QR_SIGN)!!
        assertEquals(FifMappingReason.SERVER, failure.reason)
        assertTrue(failure.message.contains("服务暂时异常"))
        assertTrue(failure.technicalDetail.contains("FIF_MAPPING_FAILED"))
        assertTrue(failure.technicalDetail.contains("扫码签到"))
        assertTrue(failure.technicalDetail.contains("HTTP 503"))
        assertFalse(failure.technicalDetail.contains("secret"))
        assertFalse(failure.message.contains("private"))
    }

    @Test fun supportsLegacyErrorsButDoesNotClassifyOtherSsoFailures() {
        assertNotNull(Exception("AI课堂登录失败: FIF 用户映射失败").fifMappingFailure(FifOperation.HOME))
        assertNull(Exception("FIF 单点登录失败").fifMappingFailure(FifOperation.HOME))
        assertNull(IOException("断网").fifMappingFailure(FifOperation.HOME))
    }

    @Test fun preservesRequestCausesAndChoosesAccurateMessages() {
        val timeout = SocketTimeoutException("sensitive request")
        val typed = mappingRequestFailure(timeout)
        assertSame(timeout, typed.cause)
        assertEquals(FifMappingReason.TIMEOUT, typed.reason)
        assertEquals(FifMappingReason.NETWORK, mappingRequestFailure(IOException()).reason)
        val messages = FifMappingReason.entries.map { FifMappingFailure(it, FifOperation.HOME).message }
        assertEquals(FifMappingReason.entries.size, messages.distinct().size)
        assertTrue(messages.none { it.contains("主登录") || it.contains("未绑定") })
    }

    @Test fun mappingParserRejectsMissingOrMalformedIdentity() {
        for (body in listOf(null, "", "not json", "{}", "{\"data\":null}", "{\"data\":{\"id\":\"\"}}", "{\"data\":{\"id\":null}}")) {
            try {
                parseFifUserMapping(body)
                fail("Expected failure for $body")
            } catch (e: FifUserMappingException) {
                assertEquals(FifMappingReason.ACCOUNT_INFO, e.reason)
            }
        }
        val info = parseFifUserMapping("""{"data":{"id":"member","userName":null,"schoolId":"school"}}""")
        assertEquals("member", info.studentId)
        assertEquals("", info.userName)
    }
}
