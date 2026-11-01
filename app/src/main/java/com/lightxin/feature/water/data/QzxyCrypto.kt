package com.lightxin.feature.water.data

import java.security.MessageDigest

/**
 * 趣智校园签名工具。算法来自官方 APK 的 `libklcxkjencry.so`，真机 6/6 复核（报告 §2.2、§9.8）。
 */
object QzxyCrypto {

    private const val SIGN_KEY = "sign-kailu-855c74a88b8b494187c99b08b8c9a744"
    private const val SMS_SECRET_SALT = "klcx"

    /** 32 位小写十六进制。 */
    fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            out.append(HEX[(b.toInt() shr 4) and 0x0F])
            out.append(HEX[b.toInt() and 0x0F])
        }
        return out.toString()
    }

    /** 密码摘要：取 md5 后 10 位大写。 */
    fun passwordDigest(plain: String): String = md5Hex(plain).takeLast(10).uppercase()

    /** 短信验证码 secret：手机号前 3 位 + 后 4 位 + "klcx" 的 md5。 */
    fun smsSecret(phone: String): String = md5Hex(phone.take(3) + phone.takeLast(4) + SMS_SECRET_SALT)

    /**
     * 签名。参与签名的字段是**硬编码白名单**，由调用方传入（见 [SIGNED_RATE_ORDER] 等），
     * 不是"全部参数升序"，因此不能把其它字段拼进来。
     */
    fun sign(loginCode: String, fields: Map<String, String>): String {
        val raw = StringBuilder()
        for (key in fields.keys.sorted()) raw.append(key).append(fields.getValue(key))
        val first = md5Hex(raw.toString() + "&key=" + loginCode)
        return md5Hex(first + "&key=" + SIGN_KEY)
    }

    /** 各接口参与签名的字段白名单（报告 §9.8、§9.10）。 */
    val SIGNED_RATE_ORDER = listOf("deviceId", "randomNumber", "telephone", "xfModel")
    val SIGNED_UPLOAD_DATA = listOf("loginCode", "telephone", "xfData")
    val SIGNED_UPLOAD_FAIL = listOf("consumeDate", "loginCode", "telephone")

    private val HEX = "0123456789abcdef".toCharArray()
}
