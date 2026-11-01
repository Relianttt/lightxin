package com.lightxin.feature.water

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lightxin.feature.water.data.QzxyCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.net.URLDecoder

/**
 * 用本地抓包回放校验签名与字段集合。HAR 被 git 忽略，缺失时整测跳过。
 * 测试里不出现任何抓包中的真实值，只比对重算结果。
 */
class QzxyHarReplayTest {

    private val harFiles = listOf(
        "../HAR/趣智校园饮水抓包.har",
        "../HAR/趣智校园搜索设备.har",
    )

    @Test
    fun `回放抓包校验签名与字段集合`() {
        val files = harFiles.map(::File).filter { it.isFile }
        Assume.assumeTrue("本地没有趣智抓包，跳过", files.isNotEmpty())

        var checked = 0
        for (file in files) {
            val root = JsonParser.parseString(file.readText()).asJsonObject
            val entries = root.getAsJsonObject("log").getAsJsonArray("entries")
            for (element in entries) {
                val request = element.asJsonObject.getAsJsonObject("request")
                if (request.get("method").asString != "POST") continue
                val url = request.get("url").asString
                if (!url.contains("china-qzxy.cn")) continue
                val path = url.substringBefore('?')
                val signedFields = when {
                    path.endsWith("/order/downRate/bluetooth/rateOrder") -> QzxyCrypto.SIGNED_RATE_ORDER
                    path.endsWith("/order/upload/bluetooth/data") -> QzxyCrypto.SIGNED_UPLOAD_DATA
                    else -> continue
                }
                val expected = if (signedFields === QzxyCrypto.SIGNED_RATE_ORDER) RATE_ORDER_FIELDS else UPLOAD_DATA_FIELDS
                val fields = parseForm(request)
                assertEquals("字段集合不符：$path", expected, fields.keys)

                val payload = signedFields.associateWith { name ->
                    fields[name] ?: error("缺少签名字段 $name")
                }
                assertEquals(
                    "签名不符：$path",
                    fields.getValue("signature"),
                    QzxyCrypto.sign(fields.getValue("loginCode"), payload),
                )
                checked++
            }
        }

        if (files.size == 2) {
            assertTrue("两条 HAR 齐备时应回放至少 6 条请求，实际 $checked", checked >= 6)
        } else {
            assertTrue("未回放到请求", checked > 0)
        }
    }

    private fun parseForm(request: JsonObject): Map<String, String> {
        val text = request.getAsJsonObject("postData").get("text").asString
        return text.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val idx = pair.indexOf('=')
            val name = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            name to value
        }
    }

    private companion object {
        /** 拦截器补齐公共字段后的完整集合，见设计 §4.4。 */
        val RATE_ORDER_FIELDS = setOf(
            "accountId", "bigTypeId", "deviceId", "loginCode", "macAddress", "macType", "phoneSystem",
            "projectId", "protocolType", "randomNumber", "signature", "smallTypeId", "telPhone",
            "telephone", "userId", "version", "xfModel",
        )
        val UPLOAD_DATA_FIELDS = setOf(
            "accountId", "loginCode", "phoneSystem", "projectId", "protocolType", "randomNumber",
            "signature", "telPhone", "telephone", "userId", "version", "xfData",
        )
    }
}
