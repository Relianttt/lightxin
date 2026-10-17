package com.lightxin.feature.running.data

import com.lightxin.core.auth.RSAUtils
import com.lightxin.feature.running.domain.TrackPoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RunningUploadPayload(
    val exerciseId: String,
    val memberId: String,
    val runningType: String,
    val startDate: String,
    val endDate: String,
    val mileKm: Double,
    val timeSeconds: Long,
    val speedKmh: Double,
    val runningRoute: List<TrackPoint>,
    val sourceInfo: String,
)

@Singleton
class RunningEncryption @Inject constructor() {

    /**
     * 上传载荷：全部键名 + 除 `runningRoute` 外的全部字段值都走 `publicKey2` RSA。
     *
     * 为什么轨迹值是明文：它是唯一的长字段（几 KB → 几十个 RSA 块拼成一个 Base64 串）。
     * 实测服务端解不开这种多块拼接的密文 —— 加密上传时记录能建、里程与速度都对，但官方 App
     * 画不出轨迹并判「运动轨迹异常，无效」；该字段改明文后同一份数据立刻判有效。短字段
     * （mile/speed/time）只占单个 RSA 块，不受影响，所以仅轨迹这一个值例外。
     *
     * 证据与取舍见 `.agents/notes/2026-10-17-decision-running-route-plaintext.md`。
     */
    fun encryptUploadPayload(payload: RunningUploadPayload): String {
        val routeJson = routeJson(payload.runningRoute)
        val fields = encryptUploadFields(plainUploadFields(payload, routeJson), RSAUtils::encryptSportData)

        val encryptedObject = JSONObject()
        fields.forEach { (key, value) -> encryptedObject.put(key, value) }

        return JSONArray().put(encryptedObject).toString()
    }

    private fun routeJson(points: List<TrackPoint>): String {
        val routeArray = JSONArray()
        points.forEach { point ->
            routeArray.put(
                JSONObject()
                    .put("latitude", formatCoordinate(point.latitude))
                    .put("longitude", formatCoordinate(point.longitude))
            )
        }
        return routeArray.toString()
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}

/** 轨迹字段名。它是唯一以明文上传的**值**，键名仍加密。 */
internal const val RUNNING_ROUTE_FIELD = "runningRoute"

/** 上传字段的明文形态（键 → 明文值），字段顺序即上传顺序。纯函数，无 Android 依赖。 */
internal fun plainUploadFields(
    payload: RunningUploadPayload,
    routeJson: String,
): LinkedHashMap<String, String> = linkedMapOf(
    "exerciseId" to payload.exerciseId,
    "memberId" to payload.memberId,
    "runningType" to payload.runningType,
    "startDate" to payload.startDate,
    "endDate" to payload.endDate,
    "mile" to formatUploadDecimal(payload.mileKm),
    "time" to payload.timeSeconds.toString(),
    "speed" to formatUploadDecimal(payload.speedKmh),
    RUNNING_ROUTE_FIELD to routeJson,
    "sourceInfo" to payload.sourceInfo,
)

/**
 * 加密边界：键全部加密；值也全部加密，**`runningRoute` 除外**。
 *
 * 纯函数，可 JVM 单测 —— 这是「轨迹明文」这条约束唯一的机械门禁，改错会立刻失败。
 */
internal fun encryptUploadFields(
    fields: Map<String, String>,
    encrypt: (String) -> String,
): LinkedHashMap<String, String> {
    val encrypted = LinkedHashMap<String, String>(fields.size)
    fields.forEach { (key, value) ->
        val encryptedValue = if (key == RUNNING_ROUTE_FIELD) value else encrypt(value)
        encrypted[encrypt(key)] = encryptedValue
    }
    return encrypted
}

private fun formatUploadDecimal(value: Double): String =
    String.format(Locale.US, "%.2f", value)
