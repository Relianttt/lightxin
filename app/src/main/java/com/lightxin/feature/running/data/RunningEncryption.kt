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
     * 上传载荷：键名与字段值都走 publicKey2 RSA —— **`runningRoute` 除外**。
     *
     * 轨迹是唯一的长字段（几 KB → 几十个 RSA 块拼成一个 Base64 串）。实测服务端解不开这种
     * 多块拼接的密文：加密上传时记录能建、里程与速度都对，但官方 App 画不出轨迹并判
     * 「运动轨迹异常，无效」；该字段改明文后同一份数据立刻判有效。
     * 短字段（mile/speed/time）只占单个 RSA 块，不受影响，所以仅轨迹这一个值例外。
     */
    fun encryptUploadPayload(payload: RunningUploadPayload): String {
        val routeArray = JSONArray()
        payload.runningRoute.forEach { point ->
            routeArray.put(
                JSONObject()
                    .put("latitude", formatCoordinate(point.latitude))
                    .put("longitude", formatCoordinate(point.longitude))
            )
        }

        val plainValues = linkedMapOf(
            "exerciseId" to payload.exerciseId,
            "memberId" to payload.memberId,
            "runningType" to payload.runningType,
            "startDate" to payload.startDate,
            "endDate" to payload.endDate,
            "mile" to formatDecimal(payload.mileKm),
            "time" to payload.timeSeconds.toString(),
            "speed" to formatDecimal(payload.speedKmh),
            "runningRoute" to routeArray.toString(),
            "sourceInfo" to payload.sourceInfo,
        )

        val encryptedObject = JSONObject()
        plainValues.forEach { (key, value) ->
            val encryptedValue = if (key == "runningRoute") value else RSAUtils.encryptSportData(value)
            encryptedObject.put(RSAUtils.encryptSportData(key), encryptedValue)
        }

        return JSONArray().put(encryptedObject).toString()
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}
