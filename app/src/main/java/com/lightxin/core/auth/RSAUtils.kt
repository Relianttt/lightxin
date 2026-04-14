package com.lightxin.core.auth

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * RSA 加密工具。
 * - publicKey  → 登录密码加密
 * - publicKey2 → 跑步数据加密
 *
 * 算法: RSA/None/PKCS1Padding，分段加密，Base64编码。
 */
object RSAUtils {

    private const val PUBLIC_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC8Zt/Ngbil9NG1Y0/8uZDIL5eR" +
        "WWn3O9zlPBsVwDcgS2lQXNnZ5hl2CryGe9SbG4gUb1EO7cgizzQl8N1yYmVzwO4kc" +
        "yOEtA1HHdBLjC8xvTOGL1G62j6nyOjJE3CMNyCDxEPHSYwuQVMzNko/kg5KyTrNSQ" +
        "t62hwRYbIK+BRwEQIDAQAB"

    private const val PUBLIC_KEY_2 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQChZH5KNDLzeINf6N+fNMAv4BJb4J" +
        "nC3JTvSWAS4IVyGVI1xXQW4odl0hXl1AUuR4FbVFDCEIjp+YEeLELPMiTOlAPg9Ps7" +
        "vp4wrrB/J7bHdlkyx+b8YztPOlU/wDzqL5H5sxmivpKrHvBRUCSMfBpN2czkuUJhbE" +
        "9zgJMyxZC1gwIDAQAB"

    private const val TRANSFORM = "RSA/None/PKCS1Padding"
    private const val MAX_ENCRYPT_BLOCK = 117 // RSA 1024-bit key → 128 bytes - 11 padding

    /** 加密登录密码 */
    fun encryptPassword(plain: String): String = encrypt(plain, PUBLIC_KEY)

    /** 加密跑步数据字段 */
    fun encryptSportData(plain: String): String = encrypt(plain, PUBLIC_KEY_2)

    private fun encrypt(plain: String, publicKeyBase64: String): String {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        val data = plain.toByteArray(Charsets.UTF_8)
        val output = mutableListOf<Byte>()
        var offset = 0

        while (offset < data.size) {
            val end = minOf(offset + MAX_ENCRYPT_BLOCK, data.size)
            val block = cipher.doFinal(data, offset, end - offset)
            output.addAll(block.toList())
            offset = end
        }

        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}
