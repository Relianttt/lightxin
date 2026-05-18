package com.lightxin.feature.aiclass.data

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.lightxin.core.network.ApiConstants
import com.lightxin.core.network.FifOkHttpClient
import com.lightxin.core.network.FifRetrofit
import com.lightxin.core.network.FifSessionManager
import com.lightxin.core.network.IzuoyeRetrofit
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
    private val izuoyeApi: IzuoyeApi,
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
                AiClassCourseMerger.mergeCourses(
                    primary = primaryCourses,
                    supplemental = supplementalCourses,
                    log = { message -> Log.i(REPOSITORY_LOG_TAG, message) },
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
            val courseRecordId = data?.courseRecordId?.takeIf { it.isNotBlank() }

            if (courseRecordId == null) {
                Result.success(null)
            } else {
                Result.success(
                    AiWorkingRecord(
                        courseRecordId = courseRecordId,
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
            val signId = data?.signId?.takeIf { it.isNotBlank() }

            if (signId == null) {
                Result.success(null)
            } else {
                Result.success(
                    AiSignInInfo(
                        signId = signId,
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
            val coursePaperList = AiClassQuizParser.extractQuizzesFromCoursePaperInfo(coursePaperResp.data)
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

            val mergedQuizList = AiClassQuizParser.mergeQuizLists(
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

    // ─── 作业相关 ───

    /** jtzy token 内存缓存 */
    private var cachedJtzy: String? = null
    private var cachedJtzyParams: JtzyParams? = null

    private data class JtzyParams(
        val cwId: String,
        val teachClassId: String,
        val schoolId: String,
        val userId: String,
        val teaCwId: String,
        val uid: String,
    )

    /** 获取课程作业列表 */
    suspend fun getHomeworkList(courseId: String, teachClassId: String): Result<List<com.lightxin.feature.aiclass.domain.AiHomework>> {
        return try {
            val auth = ensureSession()
            val userName = fifSession.getUserName().orEmpty()
            val resp = api.listStudentHomework(
                courseId = courseId,
                teachClassId = teachClassId,
                userName = userName,
                auth = auth,
            )
            val items = resp.data?.dataList.orEmpty().flatMap { group ->
                group.fileList.orEmpty().map { item ->
                    com.lightxin.feature.aiclass.domain.AiHomework(
                        id = item.id.orEmpty(),
                        title = item.jobTitle.orEmpty(),
                        endTime = item.endTime.orEmpty(),
                        jobState = item.jobState.orEmpty(),
                        score = item.score.orEmpty(),
                    )
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取作业列表", e), e))
        }
    }

    /** 获取jtzy token并缓存，返回解析后的参数 */
    private suspend fun ensureJtzy(cwId: String, teachClassId: String): Result<JtzyParams> {
        cachedJtzyParams?.let {
            if (cachedJtzy != null && it.cwId == cwId && it.teachClassId == teachClassId) {
                return Result.success(it)
            }
        }

        return try {
            val auth = ensureSession()
            val userName = fifSession.getUserName().orEmpty()
            val resp = api.getHomeworkDetailUrl(
                userName = userName,
                classId = teachClassId,
                cwId = cwId,
                auth = auth,
            )
            val url = resp.data ?: return Result.failure(Exception("获取作业详情入口失败"))

            // 从URL中解析参数
            val jtzy = extractParam(url, "jtzy") ?: return Result.failure(Exception("jtzy token 解析失败"))
            val schoolId = extractParam(url, "schoolId").orEmpty()
            val userId = extractParam(url, "userId").orEmpty()
            val teaCwId = extractParam(url, "cwId").orEmpty()
            val uid = extractParam(url, "uid").orEmpty()

            cachedJtzy = jtzy
            val params = JtzyParams(
                cwId = cwId,
                teachClassId = teachClassId,
                schoolId = schoolId,
                userId = userId,
                teaCwId = teaCwId,
                uid = uid,
            )
            cachedJtzyParams = params
            Result.success(params)
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取作业认证", e), e))
        }
    }

    /** 获取教师作业详情 */
    suspend fun getHomeworkDetail(cwId: String, teachClassId: String): Result<com.lightxin.feature.aiclass.domain.AiHomeworkDetail> {
        return try {
            val params = ensureJtzy(cwId, teachClassId).getOrThrow()
            val jtzy = cachedJtzy!!
            val resp = izuoyeApi.getTeaWorkDetail(
                schoolId = params.schoolId,
                userId = params.userId,
                teaCwId = params.teaCwId,
                uid = params.uid,
                classId = teachClassId,
                studentId = params.userId,
                jtzy = jtzy,
            )
            val data = resp.data ?: return Result.failure(Exception("作业详情为空"))
            Result.success(
                com.lightxin.feature.aiclass.domain.AiHomeworkDetail(
                    teaCwId = data.teaCwId.orEmpty(),
                    title = data.cwTitle.orEmpty(),
                    htmlContent = data.teaCwContent ?: data.showContent.orEmpty(),
                    startTime = data.cwStartTimeFormat.orEmpty(),
                    deadline = data.cwDeadlineFormat.orEmpty(),
                    teacherName = data.creatorUserName.orEmpty(),
                    cwDeadlineFormat = data.cwDeadlineFormat.orEmpty(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取作业详情", e), e))
        }
    }

    /** 获取学生提交列表 */
    suspend fun getStuWorkList(cwId: String, teachClassId: String, page: Int = 1): Result<List<com.lightxin.feature.aiclass.domain.AiStudentWork>> {
        return try {
            val params = ensureJtzy(cwId, teachClassId).getOrThrow()
            val jtzy = cachedJtzy!!
            val resp = izuoyeApi.getStuWorkList(
                schoolId = params.schoolId,
                userId = params.userId,
                teaCwId = params.teaCwId,
                classId = teachClassId,
                studentId = params.userId,
                currentPage = page.toString(),
                cwDeadline = "",
                jtzy = jtzy,
            )
            val items = resp.data?.datalist.orEmpty().map { item ->
                com.lightxin.feature.aiclass.domain.AiStudentWork(
                    stuCwId = item.stuCwId ?: item.uid.orEmpty(),
                    studentName = item.studentName.orEmpty(),
                    showContent = item.showContent.orEmpty(),
                    cwStatus = item.cwStatus ?: 0,
                    correctStatus = item.correctStatus ?: 0,
                    score = item.score.orEmpty(),
                    submitTime = item.submitTimeFormat.orEmpty(),
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(Exception(mapError("获取提交列表", e), e))
        }
    }

    /** 提交纯文本作业 */
    suspend fun submitHomework(cwId: String, teachClassId: String, textContent: String): Result<String> {
        return try {
            val params = ensureJtzy(cwId, teachClassId).getOrThrow()
            val jtzy = cachedJtzy!!

            // 获取 storageId（即 stuCwId）
            val storageResp = izuoyeApi.getStorageId(
                schoolId = params.schoolId,
                userId = params.userId,
                teaCwId = params.teaCwId,
                classId = teachClassId,
                jtzy = jtzy,
            )
            val stuCwId = storageResp.data ?: return Result.failure(Exception("获取提交ID失败"))

            // 包装为HTML并提交
            val htmlContent = toHtmlParagraph(textContent)
            val resp = izuoyeApi.submitWork(
                schoolId = params.schoolId,
                userId = params.userId,
                teaCwId = params.teaCwId,
                classId = teachClassId,
                stuCwId = stuCwId,
                showContent = htmlContent,
                jtzy = jtzy,
            )

            if (resp.code == 200 || resp.code == 0) {
                Result.success("提交成功")
            } else {
                Result.failure(Exception(resp.message ?: "提交失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapError("提交作业", e), e))
        }
    }

    /** 清除jtzy缓存（切换作业时调用） */
    fun clearJtzyCache() {
        cachedJtzy = null
        cachedJtzyParams = null
    }

    private fun extractParam(url: String, key: String): String? {
        // 优先使用 Uri 解析，自动 URL decode；失败时再回退 regex
        runCatching {
            return Uri.parse(url).getQueryParameter(key)
        }
        val regex = Regex("[?&]$key=([^&]+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun toHtmlParagraph(text: String): String {
        val encoded = TextUtils.htmlEncode(text)
            .replace("\n", "<br/>")
        return "<p>$encoded</p>"
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
            if (AiClassQrResultResolver.requiresSessionRefresh(attempt)) {
                Log.w(REPOSITORY_LOG_TAG, "qrcodeHandler redirected to loginManage, forcing SSO retry")
                auth = ensureSession(forceRefresh = true)
                attempt = executeQrCodeRequest(payload, auth)
            }

            AiClassQrResultResolver.resolveResult(attempt)
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

@Module
@InstallIn(SingletonComponent::class)
object AiClassModule {

    @Provides
    @Singleton
    fun provideAiClassApi(@FifRetrofit retrofit: Retrofit): AiClassApi =
        retrofit.create(AiClassApi::class.java)

    @Provides
    @Singleton
    fun provideIzuoyeApi(@IzuoyeRetrofit retrofit: Retrofit): IzuoyeApi =
        retrofit.create(IzuoyeApi::class.java)
}
