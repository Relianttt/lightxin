package com.lightxin.feature.water.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 固定向量来自报告 §3.2（Python 独立算出）。 */
class QzxyCryptoTest {

    @Test
    fun `密码摘要取 md5 后 10 位大写`() {
        assertEquals("4BC85B3E44", QzxyCrypto.passwordDigest("mypassword"))
    }

    @Test
    fun `短信 secret 是前三位加后四位加固定盐`() {
        assertEquals("18cd336d35b00ab76974f7052d6732a6", QzxyCrypto.smsSecret("13800000000"))
    }

    @Test
    fun `签名按白名单字段升序拼接`() {
        val fields = mapOf(
            "deviceId" to "915",
            "randomNumber" to "69020000",
            "telephone" to "13800001111",
            "xfModel" to "0",
        )
        assertEquals(
            "310b0d8ea38113dee45b71a38700d490",
            QzxyCrypto.sign("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", fields),
        )
    }

    @Test
    fun `签名白名单固定为报告列出的字段`() {
        assertEquals(listOf("deviceId", "randomNumber", "telephone", "xfModel"), QzxyCrypto.SIGNED_RATE_ORDER)
        assertEquals(listOf("loginCode", "telephone", "xfData"), QzxyCrypto.SIGNED_UPLOAD_DATA)
        assertEquals(listOf("consumeDate", "loginCode", "telephone"), QzxyCrypto.SIGNED_UPLOAD_FAIL)
    }
}
