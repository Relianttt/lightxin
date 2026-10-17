package com.lightxin.feature.labor.data

import com.lightxin.core.network.AuthExpiredException
import com.lightxin.core.network.isAuthExpired
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 劳动教育把「token 无效」塞进 200 响应体（`{"flag":false,"result":"非法访问"}`），
 * 不识别就会在界面上原样显示成「非法访问」。这里钉住识别条件，避免它被改回成普通失败。
 */
class LaborAuthRejectionTest {

    @Test
    fun `illegal access on a failed response means the session is gone`() {
        assertTrue(isLaborAuthRejection(false, "非法访问"))
        // 实测原文，前后不留空格
        assertTrue(isLaborAuthRejection(false, "非法访问"))
    }

    @Test
    fun `other failures are business errors, not auth expiry`() {
        assertFalse(isLaborAuthRejection(true, "success"))
        assertFalse(isLaborAuthRejection(null, null))
        assertFalse(isLaborAuthRejection(false, null))
        assertFalse(isLaborAuthRejection(false, "获取工时数据失败"))
        // flag 为 null（响应结构缺失）时不能擅自当成登录失效
        assertFalse(isLaborAuthRejection(null, "非法访问"))
    }

    @Test
    fun `auth expiry is detected through wrapping`() {
        assertTrue(AuthExpiredException().isAuthExpired())
        assertTrue(Exception("获取工时", AuthExpiredException()).isAuthExpired())
        assertFalse(Exception("网络异常").isAuthExpired())
        assertFalse(RuntimeException("loop").let { outer ->
            val inner = RuntimeException("inner", outer)
            outer.initCause(inner)
            outer.isAuthExpired()
        })
    }
}
