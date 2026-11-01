package com.lightxin.feature.water.domain

import java.math.RoundingMode

/**
 * 金额 / 设备名 / 时段格式化。金额单位在协议里是「厘」（1 元 = 1000 厘）。
 */

/** 厘 → "¥0.05"；第三位小数非 0 时保留 3 位："¥0.011"。 */
fun formatLi(li: Long): String {
    val negative = li < 0
    val abs = if (negative) -li else li
    val whole = abs / 1000
    val frac = abs % 1000
    val body = if (frac % 10 != 0L) {
        "$whole.${frac.toString().padStart(3, '0')}"
    } else {
        "$whole.${(frac / 10).toString().padStart(2, '0')}"
    }
    return if (negative) "¥-$body" else "¥$body"
}

/** 服务端下发的元字符串 → 与 [formatLi] 一致的展示；解析失败返回 "—"。 */
fun formatYuan(raw: String): String {
    val value = raw.trim().toBigDecimalOrNull() ?: return "—"
    val li = value.movePointRight(3).setScale(0, RoundingMode.HALF_UP)
    return formatLi(li.longValueExact())
}

/** 饮水 / 热水。按 bigTypeId 判据，不按设备名。 */
fun waterTypeName(bigTypeId: Int): String = when (bigTypeId) {
    WaterDevice.TYPE_HOT_WATER -> "热水"
    WaterDevice.TYPE_DRINKING -> "饮水"
    else -> "设备"
}

/** "热水 · 214"、"饮水 · 开水机1-1"。 */
fun deviceTitle(device: WaterDevice): String = deviceTitle(device.bigTypeId, device.roomName)

fun deviceTitle(bigTypeId: Int, roomName: String?): String =
    listOf(waterTypeName(bigTypeId), roomName?.takeIf { it.isNotBlank() })
        .filterNotNull()
        .joinToString(" · ")

/** "A36公寓 · 中楼 · 2层"。 */
fun deviceSubtitle(device: WaterDevice): String =
    deviceSubtitle(device.areaName, device.buildingName, device.floorName)

fun deviceSubtitle(areaName: String?, buildingName: String?, floorName: String?): String =
    listOf(areaName, buildingName, floorName)
        .filterNotNull()
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** 可用时段：优先用服务端文案，其次拼 deviceUseTimeList，都没有返回 null。 */
fun useTimeText(device: WaterDevice): String? {
    device.showDeviceUseTimeContent?.takeIf { it.isNotBlank() }?.let { return it }
    val ranges = device.deviceUseTimeList.orEmpty()
        .filter { !it.startTime.isNullOrBlank() && !it.endTime.isNullOrBlank() }
        .map { "${it.startTime}-${it.endTime}" }
    return ranges.takeIf { it.isNotEmpty() }?.joinToString("，")
}
