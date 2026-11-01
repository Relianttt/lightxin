package com.lightxin.feature.water.domain

/**
 * 设备码解析。
 *
 * 二维码形如 `KLCXKJ-Water,M,AABBCCDDEEFF`（取最后一个逗号后的内容）；
 * 手动输入允许 `AABBCCDDEEFF` / `aabbccddeeff` / `AA:BB:CC:DD:EE:FF`。
 * 统一返回 `AA:BB:CC:DD:EE:FF`，无法识别返回 null。
 *
 * 本校贴纸二维码的真实内容待真机确认；若实际是 URL，需要在此扩展解析并补单测。
 */
fun parseDeviceCode(raw: String): String? {
    val candidate = raw.trim().substringAfterLast(',')
    val hex = candidate
        .filterNot { it == ':' || it.isWhitespace() }
        .uppercase()
    if (hex.length != 12) return null
    if (!hex.all { it in '0'..'9' || it in 'A'..'F' }) return null
    return hex.chunked(2).joinToString(":")
}
