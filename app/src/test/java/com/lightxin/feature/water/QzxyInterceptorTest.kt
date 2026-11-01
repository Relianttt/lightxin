package com.lightxin.feature.water

import com.lightxin.core.auth.QzxySession
import com.lightxin.core.network.ApiConstants
import com.lightxin.core.network.QzxyInterceptor
import org.junit.Assert.assertEquals
import org.junit.Test

class QzxyInterceptorTest {

    private val session = QzxySession(
        telephone = "13800001111",
        loginCode = "logincode",
        userId = "1",
        accountId = "2",
        projectId = "506",
        name = "测试",
    )

    @Test
    fun `公共参数就是协议要求的 8 个字段`() {
        val common = QzxyInterceptor.commonParams(session)
        assertEquals(
            listOf(
                "loginCode", "userId", "accountId", "projectId",
                "telephone", "telPhone", "phoneSystem", "version",
            ),
            common.keys.toList(),
        )
        assertEquals(common["telephone"], common["telPhone"])
        assertEquals("android", common["phoneSystem"])
        assertEquals(ApiConstants.QZXY_VERSION, common["version"])
    }

    @Test
    fun `同名字段以业务声明的为准`() {
        val existing = listOf("protocolType" to "10", "version" to "9.9.9")
        val merged = QzxyInterceptor.mergeCommon(existing, QzxyInterceptor.commonParams(session))
        assertEquals("9.9.9", merged.toMap()["version"])
        assertEquals(9, merged.size)
    }

    @Test
    fun `rateOrder 补齐公共字段后等于协议字段集合`() {
        val business = listOf(
            "deviceId" to "915",
            "macAddress" to "C47F0E269CB1",
            "macType" to "0039",
            "bigTypeId" to "4",
            "smallTypeId" to "57",
            "protocolType" to "10",
            "randomNumber" to "69020000",
            "xfModel" to "0",
            "signature" to "sign",
        )
        val merged = QzxyInterceptor.mergeCommon(business, QzxyInterceptor.commonParams(session))
        assertEquals(
            setOf(
                "accountId", "bigTypeId", "deviceId", "loginCode", "macAddress", "macType",
                "phoneSystem", "projectId", "protocolType", "randomNumber", "signature",
                "smallTypeId", "telPhone", "telephone", "userId", "version", "xfModel",
            ),
            merged.map { it.first }.toSet(),
        )
        assertEquals(17, merged.size)
    }

    @Test
    fun `uploadData 补齐公共字段后等于协议字段集合`() {
        val business = listOf(
            "protocolType" to "10",
            "randomNumber" to "69020000",
            "xfData" to "80",
            "signature" to "sign",
        )
        val merged = QzxyInterceptor.mergeCommon(business, QzxyInterceptor.commonParams(session))
        assertEquals(
            setOf(
                "accountId", "loginCode", "phoneSystem", "projectId", "protocolType",
                "randomNumber", "signature", "telPhone", "telephone", "userId", "version", "xfData",
            ),
            merged.map { it.first }.toSet(),
        )
        assertEquals(12, merged.size)
    }
}
