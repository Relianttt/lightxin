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
            encryptedObject.put(
                RSAUtils.encryptSportData(key),
                RSAUtils.encryptSportData(value),
            )
        }

        return JSONArray().put(encryptedObject).toString()
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}
