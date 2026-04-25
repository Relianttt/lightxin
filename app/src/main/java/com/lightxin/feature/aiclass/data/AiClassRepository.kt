package com.lightxin.feature.aiclass.data

import android.util.Log
import com.lightxin.core.network.ApiConstants
import com.lightxin.core.network.FifOkHttpClient
import com.lightxin.core.network.FifRetrofit
import com.lightxin.core.network.FifSessionManager
import com.lightxin.feature.aiclass.domain.AiCourse
import com.lightxin.feature.aiclass.domain.AiQuiz
import com.lightxin.feature.aiclass.domain.AiClassQrPayload
import com.lightxin.feature.aiclass.domain.AiSignInInfo
import com.lightxin.feature.aiclass.domain.AiWorkingRecord
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val REPOSITORY_LOG_TAG = "AiClassRepo"
private const val WEBVIEW_REQUESTED_WITH = "com.lightxin"

@Singleton
class AiClassRepository @Inject constructor(
    private val api: AiClassApi,
    private val fifSession: FifSessionManager,
    private val cookieJar: CookieJar,
    @FifOkHttpClient private val fifClient: OkHttpClient,
) {
    /** 确保 FIF 会话有效，无效则自动 SSO */
    private suspend fun ensureSession(forceRefresh: Boolean = false): String {
        if (forceRefresh || !fifSession.isSessionValid()) {
            fifSession.performSso().getOrThrow()
        }
        return fifSession.buildAuthHeader()
    }

    /** 获取当前学期课程列表 */
    suspend fun getCourses(): Result<List<AiCourse>> {
        return try {
            val auth = ensureSession()

            // 先获取当前学期（selected=1）
            val termResp = api.getTermList(auth)
            val currentTerm = termResp.data?.dataList?.find { it.selected == "1" }
            val termYear = currentTerm?.year ?: return Result.failure(Exception("无法获取当前学期"))
            val term = currentTerm.num ?: return Result.failure(Exception("无法获取当前学期"))

            // 查询课程
            val resp = api.getCourses(termYear, term, auth)
            val list = resp.data?.dataList.orEmpty()
            val primaryCourses = list.map { item ->
                AiCourse(
                    stableId = buildCourseStableId(item),
                    id = item.id.orEmpty(),
                    code = item.code.orEmpty(),
                    classId = item.classId.orEmpty(),
                    courseId = item.courseId.orEmpty(),
                    courseRecordId = item.courseRecordId.orEmpty(),
                    courseName = item.courseName.orEmpty(),
                    teacherName = item.teacherName.orEmpty(),
                    studentNum = item.studentNum ?: 0,
                    teachClassId = item.teachClassId.orEmpty(),
                    cover = item.cover.orEmpty(),
                    termYear = item.termYear.orEmpty().ifBlank { termYear },
                    term = item.term.orEmpty().ifBlank { term },
                    typeName = item.typeName.orEmpty(),
                )
            }

            val supplementalCourses = runCatching {
                loadTimetableSupplementCourses(
                    auth = auth,
                    termYear = termYear,
                    term = term,
                )
            }.onFailure { error ->
                Log.w(REPOSITORY_LOG_TAG, "loadTimetableSupplementCourses failed", error)
            }.getOrDefault(emptyList())

            Result.success(
                mergeCourses(
                    primary = primaryCourses,
                    supplemental = supplementalCourses,
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取课程列表", e), e))
        }
    }

    /** 查询正在进行的课堂 */
    suspend fun getWorkingRecord(): Result<AiWorkingRecord?> {
        return try {
            val auth = ensureSession()
            val studentId = fifSession.getStudentId().orEmpty()
            val resp = api.getWorkingCourseRecord(studentId, auth)
            val data = resp.data.orEmpty().firstOrNull()

            if (data?.courseRecordId.isNullOrBlank()) {
                Result.success(null)
            } else {
                Result.success(
                    AiWorkingRecord(
                        courseRecordId = data!!.courseRecordId!!,
                        courseName = data.courseName.orEmpty(),
                        courseItemName = data.courseItemName.orEmpty(),
                        teachClassId = data.teachClassId
                            ?.takeIf { it.isNotBlank() }
                            ?: data.courseId.orEmpty(),
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取课堂状态", e), e))
        }
    }

    /** 查询签到信息 */
    suspend fun getSignInInfo(teachClassId: String, courseRecordId: String): Result<AiSignInInfo?> {
        return try {
            val auth = ensureSession()
            val studentId = fifSession.getStudentId().orEmpty()
            val resp = api.getSignInInfo(teachClassId, courseRecordId, studentId, auth)
            val data = resp.data

            if (data?.signId.isNullOrBlank()) {
                Result.success(null)
            } else {
                Result.success(
                    AiSignInInfo(
                        signId = data!!.signId!!,
                        teacherName = data.teacherName.orEmpty(),
                        hasActiveSign = true,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("查询签到", e), e))
        }
    }

    /** 数字码签到 */
    suspend fun submitSignCode(signId: String, signCode: String): Result<String> {
        return try {
            val auth = ensureSession()
            val userName = fifSession.getUserName().orEmpty()
            val resp = api.submitSignCode(signId, userName, signCode, auth)

            if (resp.status == "success") {
                Result.success(resp.message ?: "签到成功")
            } else {
                Result.failure(Exception(resp.message ?: "签到失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("签到", e), e))
        }
    }

    /** 查询课程测验列表 */
    suspend fun getQuizList(courseId: String): Result<List<AiQuiz>> {
        return try {
            val auth = ensureSession()
            val studentId = fifSession.getStudentId().orEmpty()
            val userId = fifSession.getMemberUserId().orEmpty()
            if (studentId.isBlank()) {
                return Result.failure(Exception("AI课堂身份不完整，请重新进入"))
            }

            val coursePaperResp = api.getPaperInCourseInfo(
                courseId = courseId,
                studentId = studentId,
                auth = auth,
            )
            val coursePaperList = extractQuizzesFromCoursePaperInfo(coursePaperResp.data)
            val publishPaperList = if (userId.isBlank()) {
                emptyList()
            } else {
                api.getPublishPaperListOfStudent(
                    courseId = courseId,
                    studentId = studentId,
                    userId = userId,
                    auth = auth,
                ).data.orEmpty().map { item ->
                    AiQuiz(
                        id = item.id.orEmpty(),
                        title = item.title.orEmpty(),
                        isCommitted = item.iscommited.asBoolean(),
                        status = item.status.asDisplayString(),
                        publishTime = item.publishTime.orEmpty(),
                        publishDateTime = item.publishDateTime.orEmpty(),
                        publishWeek = item.publishWeek.orEmpty(),
                        answerDurationMinutes = item.answerDuration.asIntOrNull(),
                    )
                }
            }

            val mergedQuizList = mergeQuizLists(
                primary = publishPaperList,
                secondary = coursePaperList,
            )

            when {
                mergedQuizList.isNotEmpty() -> Result.success(mergedQuizList)
                userId.isBlank() -> Result.failure(Exception("AI课堂身份不完整，请重新进入"))
                else -> Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取测验列表", e), e))
        }
    }

    /**
     * 扫码签到：用二维码 token 调用 qrcodeHandler。
     * 该接口返回 302 跳转到成功页，不是 JSON。
     */
    suspend fun submitQrCode(payload: AiClassQrPayload): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(
                REPOSITORY_LOG_TAG,
                "submitQrCode start, raw=${payload.rawValue.previewForLog(48)}, token=${payload.token.previewForLog()}, length=${payload.token.length}",
            )
            var auth = ensureSession()
            var attempt = executeQrCodeRequest(payload, auth)
            if (requiresQrSessionRefresh(attempt)) {
                Log.w(REPOSITORY_LOG_TAG, "qrcodeHandler redirected to loginManage, forcing SSO retry")
                auth = ensureSession(forceRefresh = true)
                attempt = executeQrCodeRequest(payload, auth)
            }

            resolveQrCodeResult(attempt)
        } catch (e: Exception) {
            Log.e(REPOSITORY_LOG_TAG, "submitQrCode failed", e)
            Result.failure(Exception(mapError("扫码签到", e), e))
        }
    }

    private suspend fun buildQrHandlerUrl(payload: AiClassQrPayload): String {
        val rawValue = payload.rawValue.trim()
        val baseUrl = if (rawValue.toHttpUrlOrNull() != null) {
            rawValue
        } else {
            "${ApiConstants.BASE_FIF}/coursecenter-interaction/qrcodeV2/qrcodeHandler?token=${payload.token}&openTheWay=2"
        }
        return fifSession.appendQrLoginParams(baseUrl)
    }

    private suspend fun buildQrCookieHeader(url: String): String {
        val studentId = fifSession.getStudentId().orEmpty()
        val userName = fifSession.getUserName().orEmpty()
        val requestUrl = url.toHttpUrl()
        val mergedCookies = linkedMapOf<String, String>()

        cookieJar.loadForRequest(requestUrl).forEach { cookie ->
            mergedCookies[cookie.name] = cookie.value
        }
        if (studentId.isNotBlank()) {
            mergedCookies["id"] = studentId
            mergedCookies["studentId"] = studentId
        }
        if (userName.isNotBlank()) {
            mergedCookies["currentUserName"] = userName
        }

        return mergedCookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    private fun resolveQrRedirectMessage(location: String): String {
        if (location.isBlank()) return "扫码签到失败：服务端未返回跳转地址"
        return when {
            location.contains("loginManage", ignoreCase = true) -> "扫码签到失败：AI课堂登录态已失效"
            location.contains("login", ignoreCase = true) -> "扫码签到失败：AI课堂登录态已失效"
            location.contains("course", ignoreCase = true) -> "扫码签到失败：二维码跳转到了课程页"
            else -> "扫码签到失败：未识别的跳转结果"
        }
    }

    private fun requiresQrSessionRefresh(result: QrRequestResult): Boolean =
        result.code == 302 && result.location.contains("loginManage", ignoreCase = true)

    private fun resolveQrCodeResult(result: QrRequestResult): Result<String> {
        return if (result.code == 302 && result.location.contains("codeExpired", ignoreCase = true)) {
            Result.failure(Exception("二维码已过期"))
        } else if (result.code == 302 && result.location.contains("signSuccess", ignoreCase = true)) {
            Result.success("扫码签到成功")
        } else if (result.code == 302) {
            Result.failure(Exception(resolveQrRedirectMessage(result.location)))
        } else {
            Result.failure(Exception("签到失败（HTTP ${result.code}）"))
        }
    }

    private suspend fun executeQrCodeRequest(payload: AiClassQrPayload, auth: String): QrRequestResult {
        val url = buildQrHandlerUrl(payload)
        val cookieHeader = buildQrCookieHeader(url)

        val noRedirectClient = fifClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("authorization", auth)
            .header("Visit-Type", "mobile")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/122.0.0.0 Mobile Safari/537.36",
            )
            .header("X-Requested-With", WEBVIEW_REQUESTED_WITH)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            )
            .header("Upgrade-Insecure-Requests", "1")
        if (cookieHeader.isNotBlank()) {
            requestBuilder.header("Cookie", cookieHeader)
        }
        val request = requestBuilder.build()

        val response = noRedirectClient.newCall(request).execute()
        val code = response.code
        val location = response.header("Location").orEmpty()
        response.close()

        Log.i(
            REPOSITORY_LOG_TAG,
            "qrcodeHandler finished, httpCode=$code, location=${location.previewForLog(96)}, url=${url.previewForLog(96)}",
        )

        return QrRequestResult(
            code = code,
            location = location,
            requestUrl = url,
        )
    }

    private fun mapError(action: String, error: Exception): String {
        return when (error) {
            is HttpException -> when {
                error.code() == 401 -> "FIF 登录已失效，请重试"
                error.code() >= 500 -> "${action}接口暂时异常"
                else -> "${action}失败（HTTP ${error.code()}）"
            }
            is IOException -> "网络异常，请检查连接"
            else -> error.message ?: "${action}失败"
        }
    }

    private suspend fun loadTimetableSupplementCourses(
        auth: String,
        termYear: String,
        term: String,
    ): List<AiCourse> {
        val schoolId = fifSession.getSchoolId().orEmpty()
        val memberId = fifSession.getMemberUserId().orEmpty()
        if (schoolId.isBlank() || memberId.isBlank()) {
            Log.w(REPOSITORY_LOG_TAG, "loadTimetableSupplementCourses skipped: schoolId or memberId blank")
            return emptyList()
        }

        val response = api.getTimetableInfo(
            schoolId = schoolId,
            memberId = memberId,
            auth = auth,
        )
        val courses = extractTimetableCourses(
            element = response.data,
            termYear = termYear,
            term = term,
        )
        Log.i(REPOSITORY_LOG_TAG, "Timetable supplement: ${courses.size} courses loaded, schoolId=$schoolId, memberId=$memberId")
        return courses
    }
}

private fun String.previewForLog(maxLen: Int = 16): String {
    if (isBlank()) return "<blank>"
    return if (length <= maxLen) this else take(maxLen) + "..."
}

private data class QrRequestResult(
    val code: Int,
    val location: String,
    val requestUrl: String,
)

private fun Any?.asBoolean(): Boolean {
    return when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        is String -> equals("true", ignoreCase = true) || this == "1"
        else -> false
    }
}

private fun Any?.asIntOrNull(): Int? {
    return when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }
}

private fun Any?.asDisplayString(): String {
    return when (this) {
        null -> ""
        is String -> this
        is Number -> toInt().toString()
        is Boolean -> if (this) "1" else "0"
        else -> toString()
    }
}

private fun buildCourseStableId(item: AiClassCourseResponse.CourseItem): String {
    return listOf(
        item.id,
        item.classId,
        item.teachClassId,
        item.courseId,
        item.courseRecordId,
        item.termYear,
        item.term,
        item.courseName,
        item.teacherName,
        item.typeName,
    ).filter { !it.isNullOrBlank() }
        .joinToString("|")
        .ifBlank { "course:${item.hashCode()}" }
}

private fun buildCourseStableId(course: AiCourse): String {
    return listOf(
        course.id,
        course.classId,
        course.teachClassId,
        course.courseId,
        course.courseRecordId,
        course.termYear,
        course.term,
        course.courseName,
        course.teacherName,
        course.typeName,
    ).filter { it.isNotBlank() }
        .joinToString("|")
        .ifBlank { "course:${course.hashCode()}" }
}

private fun mergeCourses(
    primary: List<AiCourse>,
    supplemental: List<AiCourse>,
): List<AiCourse> {
    val merged = primary.map { it.normalize() }.toMutableList()

    supplemental.forEach { course ->
        val normalized = course.normalize()
        val exactMatchIndex = merged.indexOfFirst { it.matchesByIdentity(normalized) }
        if (exactMatchIndex >= 0) {
            Log.i(REPOSITORY_LOG_TAG, "Course merge: exact match \"${normalized.courseName}\" (typeName=${normalized.typeName}) with index $exactMatchIndex")
            merged[exactMatchIndex] = merged[exactMatchIndex].mergeWithSupplement(normalized)
            return@forEach
        }

        val fuzzyMatchIndexes = merged.withIndex()
            .filter { (_, existing) -> existing.matchesByName(normalized) }
            .map { it.index }

        if (fuzzyMatchIndexes.size == 1) {
            val index = fuzzyMatchIndexes.first()
            Log.i(REPOSITORY_LOG_TAG, "Course merge: fuzzy match \"${normalized.courseName}\" (typeName=${normalized.typeName}) with index $index")
            merged[index] = merged[index].mergeWithSupplement(normalized)
        } else {
            Log.i(REPOSITORY_LOG_TAG, "Course merge: new entry \"${normalized.courseName}\" (typeName=${normalized.typeName}, fuzzyMatches=${fuzzyMatchIndexes.size})")
            merged += normalized
        }
    }

    Log.i(REPOSITORY_LOG_TAG, "Course merge result: primary=${primary.size}, supplemental=${supplemental.size}, merged=${merged.size}")
    return merged
}

private fun AiCourse.normalize(): AiCourse {
    val normalized = copy(
        termYear = termYear.trim(),
        term = term.trim(),
        courseName = courseName.trim(),
        teacherName = teacherName.trim(),
        typeName = typeName.trim(),
    )
    return normalized.copy(stableId = buildCourseStableId(normalized))
}

private fun AiCourse.mergeKey(): String {
    return teachClassId.ifBlank { classId }
        .ifBlank { id }
        .ifBlank { "$termYear|$term|$courseName|$teacherName|$typeName" }
}

private fun AiCourse.matchesByIdentity(other: AiCourse): Boolean {
    val thisIds = listOf(id, classId, teachClassId, courseId)
        .filter { it.isNotBlank() }
        .toSet()
    val otherIds = listOf(other.id, other.classId, other.teachClassId, other.courseId)
        .filter { it.isNotBlank() }
        .toSet()
    if (thisIds.isEmpty() || otherIds.isEmpty()) return false
    if (thisIds.intersect(otherIds).isEmpty()) return false
    // 同一门课的理论/实验班 ID 可能不同班——若 typeName 都有值但不同，视为独立条目
    if (typeName.isNotBlank() && other.typeName.isNotBlank() && typeName != other.typeName) {
        return false
    }
    return true
}

private fun AiCourse.matchesByName(other: AiCourse): Boolean {
    if (termYear != other.termYear || term != other.term) return false
    if (courseName.normalizedCourseName() != other.courseName.normalizedCourseName()) return false
    if (teacherName.isNotBlank() && other.teacherName.isNotBlank() && teacherName != other.teacherName) {
        return false
    }
    if (typeName.isNotBlank() && other.typeName.isNotBlank() && typeName != other.typeName) {
        return false
    }
    return true
}

private fun AiCourse.mergeWithSupplement(supplement: AiCourse): AiCourse {
    val merged = copy(
        id = id.ifBlank { supplement.id },
        code = code.ifBlank { supplement.code },
        classId = classId.ifBlank { supplement.classId },
        courseId = courseId.ifBlank { supplement.courseId },
        courseRecordId = courseRecordId.ifBlank { supplement.courseRecordId },
        courseName = courseName.ifBlank { supplement.courseName },
        teacherName = teacherName.ifBlank { supplement.teacherName },
        studentNum = if (studentNum > 0) studentNum else supplement.studentNum,
        teachClassId = teachClassId.ifBlank { supplement.teachClassId },
        cover = cover.ifBlank { supplement.cover },
        termYear = termYear.ifBlank { supplement.termYear },
        term = term.ifBlank { supplement.term },
        typeName = typeName.ifBlank { supplement.typeName },
    )
    return merged.copy(stableId = buildCourseStableId(merged))
}

private fun extractTimetableCourses(
    element: JsonElement?,
    termYear: String,
    term: String,
): List<AiCourse> {
    val results = LinkedHashMap<String, AiCourse>()

    fun walk(node: JsonElement?) {
        when {
            node == null || node.isJsonNull -> Unit
            node.isJsonArray -> node.asJsonArray.forEach(::walk)
            node.isJsonObject -> {
                val obj = node.asJsonObject
                obj.toTimetableCourseOrNull(termYear, term)?.let { course ->
                    // 同一 teachClassId 之下理论/实验是不同班次，key 带上 typeName 区分
                    val baseKey = course.teachClassId.ifBlank { course.classId }
                    val dedupKey = if (course.typeName.isNotBlank()) "$baseKey|${course.typeName}" else baseKey
                    results.putIfAbsent(dedupKey.ifBlank { course.stableId }, course)
                }
                obj.entrySet().forEach { (_, child) -> walk(child) }
            }
        }
    }

    walk(element)
    Log.i(REPOSITORY_LOG_TAG, "Timetable extraction done: ${results.size} courses found")
    return results.values.toList()
}

private fun JsonObject.toTimetableCourseOrNull(termYear: String, term: String): AiCourse? {
    val teachClassId = stringValue("classId", "classIds", "teachClassId", "id").trim()
    val courseName = stringValue("courseName", "courseItemName", "name", "title").trim()
    if (teachClassId.isBlank() || courseName.isBlank()) {
        return null
    }

    val className = stringValue("className", "classNames", "name", "classNameStr")
    val typeName = parseTypeName(className)
    val course = AiCourse(
        stableId = "",
        id = teachClassId,
        code = "",
        classId = teachClassId,
        courseId = teachClassId,
        courseRecordId = "",
        courseName = courseName,
        teacherName = stringValue("teacherName", "teacher").trim(),
        studentNum = 0,
        teachClassId = teachClassId,
        cover = "",
        termYear = termYear,
        term = term,
        typeName = typeName,
    )
    Log.i(
        REPOSITORY_LOG_TAG,
        "Timetable entry parsed: name=$courseName, teachClassId=$teachClassId, typeName=$typeName, className=$className",
    )
    return course.copy(stableId = buildCourseStableId(course))
}

private fun extractQuizzesFromCoursePaperInfo(element: JsonElement?): List<AiQuiz> {
    val results = mutableListOf<AiQuiz>()

    fun walk(node: JsonElement?) {
        when {
            node == null || node.isJsonNull -> Unit
            node.isJsonArray -> node.asJsonArray.forEach(::walk)
            node.isJsonObject -> {
                val obj = node.asJsonObject
                obj.toAiQuizOrNull()?.let(results::add)
                obj.entrySet().forEach { (_, child) -> walk(child) }
            }
        }
    }

    walk(element)
    return results
        .distinctBy { it.id.ifBlank { "${it.title}|${it.publishDateTime}|${it.publishTime}" } }
}

private fun mergeQuizLists(
    primary: List<AiQuiz>,
    secondary: List<AiQuiz>,
): List<AiQuiz> {
    val merged = LinkedHashMap<String, AiQuiz>()

    primary.forEach { quiz ->
        merged[quiz.stableKey()] = quiz
    }
    secondary.forEach { quiz ->
        val key = quiz.stableKey()
        merged[key] = merged[key]?.fillMissingFrom(quiz) ?: quiz
    }

    return merged.values.toList()
}

private fun AiQuiz.fillMissingFrom(fallback: AiQuiz): AiQuiz {
    return copy(
        id = id.ifBlank { fallback.id },
        title = title.ifBlank { fallback.title },
        status = status.ifBlank { fallback.status },
        publishTime = publishTime.ifBlank { fallback.publishTime },
        publishDateTime = publishDateTime.ifBlank { fallback.publishDateTime },
        publishWeek = publishWeek.ifBlank { fallback.publishWeek },
        answerDurationMinutes = answerDurationMinutes ?: fallback.answerDurationMinutes,
    )
}

private fun AiQuiz.stableKey(): String {
    return id.ifBlank { "${title}|${publishDateTime}|${publishTime}|${publishWeek}" }
}

private fun JsonObject.toAiQuizOrNull(): AiQuiz? {
    val id = stringValue("id", "paperId", "refPaperId")
    val title = stringValue("title", "paperTitle", "name", "paperName")
    if (title.isBlank()) return null

    return AiQuiz(
        id = id,
        title = title,
        isCommitted = anyValue("iscommited", "isCommitted", "committed", "submitStatus").asBoolean(),
        status = anyValue("status", "paperStatus", "submitStatus").asDisplayString(),
        publishTime = stringValue("publishTime", "startTime"),
        publishDateTime = stringValue("publishDateTime", "createTime", "publishDate"),
        publishWeek = stringValue("publishWeek", "week"),
        answerDurationMinutes = anyValue("answerDuration", "duration", "limitTime").asIntOrNull(),
    )
}

private fun JsonObject.stringValue(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { !it.isJsonNull }?.let { element ->
            if (element.isJsonPrimitive) element.asJsonPrimitive.asString else null
        }?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun JsonObject.anyValue(vararg keys: String): Any? {
    return keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { !it.isJsonNull }?.toPrimitiveValue()
    }
}

private fun parseTypeName(rawClassName: String): String {
    return when {
        rawClassName.contains("(实验)") || rawClassName.contains("实验") -> "实验"
        rawClassName.contains("(理论)") || rawClassName.contains("理论") -> "理论"
        else -> ""
    }
}

private fun String.normalizedCourseName(): String {
    return replace(Regex("\\s+"), "")
        .replace(Regex("[（(]实验[）)]|[（(]理论[）)]"), "")
        .trim()
}

private fun JsonElement.toPrimitiveValue(): Any? {
    if (!isJsonPrimitive) return null
    val primitive = asJsonPrimitive
    return when {
        primitive.isBoolean -> primitive.asBoolean
        primitive.isNumber -> primitive.asNumber
        primitive.isString -> primitive.asString
        else -> null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AiClassModule {

    @Provides
    @Singleton
    fun provideAiClassApi(@FifRetrofit retrofit: Retrofit): AiClassApi =
        retrofit.create(AiClassApi::class.java)
}
