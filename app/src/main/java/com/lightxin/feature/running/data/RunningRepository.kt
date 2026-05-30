package com.lightxin.feature.running.data

import android.os.Build
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.location.CoordinateConverter
import com.lightxin.feature.running.domain.RunningDashboard
import com.lightxin.feature.running.domain.RunningResult
import com.lightxin.feature.running.domain.RunningSnapshot
import com.lightxin.feature.running.domain.RunningStartInfo
import com.lightxin.feature.running.domain.SimConfig
import com.lightxin.feature.running.domain.TrackPoint
import com.lightxin.feature.running.domain.TrajectoryGenerator
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject

@ViewModelScoped
class RunningRepository @Inject constructor(
    private val api: RunningApi,
    private val tokenManager: TokenManager,
    private val encryption: RunningEncryption,
) {

    suspend fun getDashboard(): Result<RunningDashboard> {
        return try {
            val params = sportsAuthParams()
            coroutineScope {
                val dashboardDeferred = async { api.getStartIndex(params) }
                val typeDeferred = async { api.checkDsStudent(params) }

                val dashboardJson = dashboardDeferred.await()
                val studentTypeJson = typeDeferred.await()
                dashboardJson.requireSportsSuccess("获取跑步首页失败")
                studentTypeJson.requireSportsSuccess("获取学生类型失败")

                val studentTypeLabel = studentTypeJson.lookupString("result", "msg")
                    .ifBlank { "学生类型未识别" }

                // 大一大二并行拉课外跑步进度 + 俱乐部摘要（按接口独立降级，失败不互相牵连）
                val isJunior = SportsGradeMapper.isJuniorGrade(studentTypeLabel)
                val juniorParams = if (isJunior) params + ("grade" to studentTypeLabel) else params
                val extraDeferred = if (isJunior) async { runCatching { api.getExtraInfo(juniorParams) }.getOrNull() } else null
                val clubDeferred = if (isJunior) async { runCatching { api.getClubInfo(juniorParams) }.getOrNull() } else null
                val detailDeferred = if (isJunior) {
                    async { runCatching { api.getExtraDetailInfo(juniorParams + ("runningType" to "1")) }.getOrNull() }
                } else {
                    null
                }
                val extraJson = extraDeferred?.await()
                val extraDetailJson = detailDeferred?.await()
                val extraDetail = SportsGradeMapper.parseExtraDetail(extraDetailJson)
                val clubSummary = SportsGradeMapper.parseClubSummary(clubDeferred?.await())

                val startIndexCompletedKm = dashboardJson.lookupDouble("completedKM", "dsTaskMile")
                val startIndexTargetKm = dashboardJson.lookupDouble("dsTaskThreshold", "totalTaskThreshold")
                val startIndexLeftKm = dashboardJson.lookupDouble("leftKM")

                // 大二目标值改取服务端下发的课外跑步计划（extraInfo）；大三保持 startIndex 字段。不本地按性别/年级计算。
                val (extraCompletedKm, extraPlanKm) = SportsGradeMapper.parseExtraProgress(extraJson)
                val completedKm = when {
                    isJunior && extraPlanKm > 0.0 -> extraCompletedKm
                    isJunior && extraDetail.completedMileKm > 0.0 -> extraDetail.completedMileKm
                    else -> startIndexCompletedKm
                }
                val taskTargetKm = when {
                    isJunior && extraPlanKm > 0.0 -> extraPlanKm
                    isJunior && (extraDetail.completedMileKm > 0.0 || extraDetail.surplusMileKm > 0.0) ->
                        extraDetail.completedMileKm + extraDetail.surplusMileKm
                    startIndexTargetKm > 0.0 -> startIndexTargetKm
                    completedKm > 0.0 || startIndexLeftKm > 0.0 -> completedKm + startIndexLeftKm
                    else -> 0.0
                }
                val leftKm = when {
                    isJunior && extraDetail.surplusMileKm > 0.0 -> extraDetail.surplusMileKm
                    startIndexLeftKm > 0.0 -> startIndexLeftKm
                    else -> (taskTargetKm - completedKm).coerceAtLeast(0.0)
                }
                val startIndexTodayKm = dashboardJson.lookupDouble("todayKM", "todayMile")
                val todayKm = if (isJunior) {
                    extraDetail.todayMileKm.takeIf { it > 0.0 } ?: startIndexTodayKm
                } else {
                    startIndexTodayKm
                }
                val startIndexSingleRunKm = dashboardJson.lookupDouble("validSettingLJ", "mixOnceMile")
                val singleRunTargetKm = if (isJunior) {
                    extraDetail.mixOnceMileKm.takeIf { extraDetailJson != null && it > 0.0 } ?: startIndexSingleRunKm
                } else {
                    startIndexSingleRunKm
                }
                val startIndexMaxKm = dashboardJson.lookupDouble("maxKM")
                val maxKm = if (isJunior) extraDetail.maxMileKm.takeIf { it > 0.0 } ?: startIndexMaxKm else startIndexMaxKm
                val maxKmDate = if (isJunior) {
                    extraDetail.maxMileDate.ifBlank { dashboardJson.lookupString("maxKmDate") }
                } else {
                    dashboardJson.lookupString("maxKmDate")
                }

                Result.success(
                    RunningDashboard(
                        todayKm = todayKm,
                        completedKm = completedKm,
                        leftKm = leftKm,
                        taskTargetKm = taskTargetKm,
                        singleRunTargetKm = singleRunTargetKm,
                        maxKm = maxKm,
                        maxKmDate = maxKmDate,
                        dsFlag = dashboardJson.lookupBoolean("dsFlag", "taskFlag"),
                        studentTypeLabel = studentTypeLabel,
                        clubSummary = clubSummary,
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "获取跑步首页失败", e))
        }
    }

    suspend fun getClubDetail(): Result<List<com.lightxin.feature.running.exercise.domain.ClubTask>> {
        return try {
            val params = sportsAuthParams()
            val grade = api.checkDsStudent(params).lookupString("result", "msg")
            val clubJson = api.getClubDetail(params + ("grade" to grade))
            clubJson.requireSportsSuccess("获取俱乐部详情失败")
            Result.success(ClubDetailMapper.parseTasks(clubJson))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "获取俱乐部详情失败", e))
        }
    }

    suspend fun currentStudentCode(): String = tokenManager.getUserCode().orEmpty()

    /** 轮询锻炼考勤打卡结果。返回 true 表示打卡成功（hasResult=YES）。 */
    suspend fun pollQrcodeResult(timestamp: Long): Result<Boolean> {
        return try {
            val studentCode = tokenManager.getUserCode().orEmpty()
            val json = api.getQrcodeResult(
                studentCode = studentCode.toPlainBody(),
                timestamp = timestamp.toString().toPlainBody(),
            )
            val hasResult = json.get("data")
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("hasResult")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            Result.success(hasResult.equals("YES", ignoreCase = true))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "查询打卡结果失败", e))
        }
    }

    private fun String.toPlainBody(): okhttp3.RequestBody =
        okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), this)

    suspend fun startRunning(extraId: String? = null): Result<RunningStartInfo> {
        return try {
            val params = sportsAuthParams()
            val grade = api.checkDsStudent(params).lookupString("result", "msg")
            // 大一大二无 startRunning.do：会话标识来自 extraInfo.do(extraId) + extraDetailInfo.do(memberId/mixOnceMile)
            if (SportsGradeMapper.isJuniorGrade(grade)) {
                return startJuniorRunning(params + ("grade" to grade))
            }
            val startParams = params.toMutableMap().apply {
                if (!extraId.isNullOrBlank()) {
                    put("extraId", extraId)
                }
            }
            val response = api.startRunning(startParams)
            response.requireSportsSuccess("开始跑步失败")
            val exerciseId = response.lookupString("extraId")
            if (exerciseId.isBlank()) {
                return Result.failure(
                    Exception(
                        response.lookupString("result", "msg")
                            .ifBlank { "开始跑步失败" }
                    )
                )
            }

            Result.success(
                RunningStartInfo(
                    exerciseId = exerciseId,
                    memberId = response.lookupString("memberId"),
                    mixOnceMileKm = response.lookupDouble("mixOnceMile").takeIf { it > 0.0 } ?: 1.0,
                    validSettingLj = response.lookupString("validSettingLJ"),
                    taskFlag = response.lookupString("taskFlag"),
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "开始跑步失败", e))
        }
    }

    /** 大一大二课外跑步会话：extraId 取自 extraInfo.do，memberId/mixOnceMile 取自 extraDetailInfo.do。 */
    private suspend fun startJuniorRunning(juniorParams: Map<String, String>): Result<RunningStartInfo> {
        val extraJson = api.getExtraInfo(juniorParams)
        val exerciseId = SportsGradeMapper.parseExtraId(extraJson)
        if (exerciseId.isBlank()) {
            return Result.failure(Exception("未获取到课外跑步任务，无法开始跑步"))
        }
        val extraDetail = SportsGradeMapper.parseExtraDetail(
            api.getExtraDetailInfo(juniorParams + ("runningType" to "1"))
        )
        return Result.success(
            RunningStartInfo(
                exerciseId = exerciseId,
                memberId = extraDetail.memberId,
                mixOnceMileKm = extraDetail.mixOnceMileKm,
            )
        )
    }

    suspend fun submitSimulation(
        config: SimConfig,
        overridePoints: List<TrackPoint>? = null,
        overrideIsBd09: Boolean = false,
    ): Result<RunningResult> {
        return try {
            val startInfo = startRunning().getOrElse { return Result.failure(it) }
            val trajectory: List<TrackPoint>
            val isBd09: Boolean
            if (overridePoints != null && overridePoints.size >= 2) {
                trajectory = overridePoints
                isBd09 = overrideIsBd09
            } else {
                trajectory = TrajectoryGenerator.generate(config)
                isBd09 = true
            }
            val snapshot = RunningSnapshot(
                startInfo = startInfo,
                startTimeMillis = config.startTimeMillis,
                durationSeconds = config.durationMinutes * 60L,
                distanceMeters = config.distanceKm * 1000.0,
                points = trajectory,
                pointsAreBd09 = isBd09,
            )
            uploadSnapshot(snapshot)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "模拟提交失败", e))
        }
    }

    suspend fun uploadTrackedRun(snapshot: RunningSnapshot): Result<RunningResult> =
        uploadSnapshot(snapshot)

    private suspend fun uploadSnapshot(snapshot: RunningSnapshot): Result<RunningResult> {
        return try {
            val serverTime = parseServerTime(api.getServerTime().string())
            val distanceKm = snapshot.distanceMeters / 1000.0
            val durationSeconds = snapshot.durationSeconds.coerceAtLeast(1L)
            val speedKmh = distanceKm / durationSeconds * 3600.0
            val route = if (snapshot.pointsAreBd09) {
                snapshot.points
            } else {
                snapshot.points.map { point ->
                    val bdPoint = CoordinateConverter.wgs84ToBd09(point.latitude, point.longitude)
                    TrackPoint(
                        latitude = bdPoint.latitude,
                        longitude = bdPoint.longitude,
                        timestampMillis = point.timestampMillis,
                    )
                }
            }

            val payload = RunningUploadPayload(
                exerciseId = snapshot.startInfo.exerciseId,
                memberId = snapshot.startInfo.memberId,
                runningType = snapshot.startInfo.runningType,
                startDate = formatDateTime(snapshot.startTimeMillis),
                endDate = serverTime,
                mileKm = distanceKm,
                timeSeconds = durationSeconds,
                speedKmh = speedKmh,
                runningRoute = route,
                sourceInfo = Build.MODEL ?: "Android",
            )

            val encryptedList = encryption.encryptUploadPayload(payload)
            val response = api.uploadRunningRecord(encryptedList)
            val resultCode = response.lookupString("result")
            val uploadId = response.lookupString("id")
            val success = resultCode.equals("OK", ignoreCase = true) && uploadId.isValidUploadId()
            val message = when {
                success -> "上传成功"
                resultCode.equals("OK", ignoreCase = true) ->
                    "服务器未生成有效跑步记录（id=${uploadId.ifBlank { "空" }}），本次里程可能未入账"
                else -> response.lookupString("msg", "result").ifBlank { "上传失败" }
            }

            if (!success) {
                return Result.failure(Exception(message))
            }

            Result.success(
                RunningResult(
                    success = true,
                    message = message,
                    uploadId = uploadId,
                    startDate = payload.startDate,
                    endDate = payload.endDate,
                    distanceKm = distanceKm,
                    durationSeconds = durationSeconds,
                    speedKmh = speedKmh,
                    pointCount = route.size,
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "上传跑步记录失败", e))
        }
    }

    private suspend fun sportsAuthParams(): Map<String, String> {
        val accessToken = tokenManager.getAccessToken().orEmpty()
        val userCode = tokenManager.getUserCode().orEmpty()
        val userName = tokenManager.getUserName().orEmpty()
        val userType = tokenManager.getUserType().orEmpty().ifBlank { "1" }

        if (userCode.isBlank() || accessToken.isBlank()) {
            throw IllegalStateException("登录信息已失效，请重新登录")
        }

        return linkedMapOf(
            "studentCode" to userCode,
            "accessToken" to accessToken,
            "access_token" to accessToken,
            "userCode" to userCode,
            "_userCode" to userCode,
            "xh" to userCode,
            "userName" to userName,
            "_userType[0]" to userType,
            "_userType[1]" to userType,
        )
    }

    private fun parseServerTime(raw: String): String {
        val text = raw.trim().removeSurrounding("\"")
        if (text.isBlank()) return formatDateTime(System.currentTimeMillis())
        if (!text.startsWith("{")) return text.takeIf(::isValidDateTimeText) ?: formatDateTime(System.currentTimeMillis())

        val json = com.google.gson.JsonParser.parseString(text).asJsonObject
        val candidate = json.lookupString("serverTime", "data", "result")
        return candidate.takeIf(::isValidDateTimeText) ?: formatDateTime(System.currentTimeMillis())
    }

    private fun formatDateTime(timeMillis: Long): String {
        val instant = Instant.ofEpochMilli(timeMillis)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return localDateTime.format(FORMATTER)
    }

    private fun JsonObject.lookupString(vararg keys: String): String {
        keys.forEach { key ->
            val value = getAsPlainString(lookupElement(key))
            if (value.isNotBlank()) {
                return value
            }
        }
        return ""
    }

    private fun JsonObject.requireSportsSuccess(fallback: String) {
        val flagElement = get("flag")
        if (flagElement != null && !flagElement.isJsonNull) {
            val success = when {
                flagElement.isJsonPrimitive && flagElement.asJsonPrimitive.isBoolean -> flagElement.asBoolean
                else -> flagElement.asString.equals("true", ignoreCase = true)
            }
            if (!success) {
                throw IllegalStateException(
                    lookupString("result", "msg")
                        .ifBlank { fallback }
                )
            }
        }
    }

    private fun JsonObject.lookupBoolean(vararg keys: String): Boolean {
        keys.forEach { key ->
            val element = lookupElement(key)
            if (element != null && !element.isJsonNull) {
                return when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isBoolean -> element.asBoolean
                    else -> element.asString.equals("true", ignoreCase = true)
                }
            }
        }
        return false
    }

    private fun JsonObject.lookupDouble(vararg keys: String): Double =
        lookupString(*keys).toDoubleOrNull() ?: 0.0

    private fun JsonObject.lookupElement(key: String): JsonElement? {
        get(key)?.takeUnless { it.isJsonNull }?.let { return it }
        get("data")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get(key)
            ?.takeUnless { it.isJsonNull }
            ?.let { return it }
        firstRowObject()
            ?.get(key)
            ?.takeUnless { it.isJsonNull }
            ?.let { return it }
        return null
    }

    private fun JsonObject.firstRowObject(): JsonObject? {
        val rows = get("rows") ?: return null
        if (!rows.isJsonArray || rows.asJsonArray.size() == 0) {
            return null
        }
        val first = rows.asJsonArray[0]
        return if (first.isJsonObject) first.asJsonObject else null
    }

    private fun getAsPlainString(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        return when {
            element.isJsonPrimitive -> element.asString.orEmpty()
            element.isJsonObject -> element.asJsonObject.lookupString("id")
            else -> element.toString()
        }
    }

    private fun isValidDateTimeText(value: String): Boolean {
        return try {
            LocalDateTime.parse(value, FORMATTER)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private fun String.isValidUploadId(): Boolean =
        isNotBlank() && this != "0" && !equals("null", ignoreCase = true)

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}
