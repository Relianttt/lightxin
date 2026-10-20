package com.lightxin.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.auth.AccountSession
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.fifDataStore: DataStore<Preferences> by preferencesDataStore(name = "fif_session")

/**
 * FIF AI课堂独立会话管理。
 * 与校内 TokenManager 完全分离，管理 FIF SSO 登录态。
 */
@Singleton
class FifSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    @FifOkHttpClient private val fifClient: OkHttpClient,
    private val cookieJar: FifCookieJar,
    private val accountSession: AccountSession,
) {
    private val sessionMutex = Mutex()
    companion object {
        private val KEY_FIF_TOKEN = stringPreferencesKey("fif_token")
        private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_SCHOOL_ID = stringPreferencesKey("school_id")
        private val KEY_MEMBER_USER_ID = stringPreferencesKey("member_user_id")
        private val KEY_FIF_USED = booleanPreferencesKey("fif_used")
    }

    suspend fun getStudentId(): String? =
        context.fifDataStore.data.first()[KEY_STUDENT_ID]

    suspend fun getUserName(): String? =
        context.fifDataStore.data.first()[KEY_USER_NAME]

    suspend fun getFifToken(): String? =
        context.fifDataStore.data.first()[KEY_FIF_TOKEN]

    suspend fun getSchoolId(): String? =
        context.fifDataStore.data.first()[KEY_SCHOOL_ID]

    suspend fun getMemberUserId(): String? =
        context.fifDataStore.data.first()[KEY_MEMBER_USER_ID]

    /**
     * 用户是否用过 AI课堂。
     *
     * 会话体检的 `Skipped` 不能拿“有没有 FIF token”当判据：被顶号之后 SSO 失败，
     * token 压根不会落盘，于是“用过但正被顶”会被误判成“从没用过”而永远不提示
     * （首页横幅一直不亮，进 AI课堂才发现）。这里单独记一个只增不减的标记。
     */
    suspend fun isFifUsed(): Boolean =
        context.fifDataStore.data.first()[KEY_FIF_USED] == true

    suspend fun isSessionValid(): Boolean =
        !getStudentId().isNullOrBlank() &&
            !getFifToken().isNullOrBlank() &&
            hasSessionCookie()

    suspend fun hasSessionCookie(): Boolean = withContext(Dispatchers.IO) {
        val fifUrl = ApiConstants.BASE_FIF.toHttpUrl()
        cookieJar.loadForRequest(fifUrl).any { cookie ->
            cookie.name.equals("SESSION", ignoreCase = true) && cookie.value.isNotBlank()
        }
    }

    /**
     * 执行完整的 SSO 登录链路:
     * 1. 携带校内 token 请求 aiitpass.fifedu.com
     * 2. 从 302 Location 提取 FIF token
     * 3. 跟随重定向建立 Cookie 会话
     * 4. 调用 getAiktUserIdByMemberId 获取 FIF 用户标识
     */
    suspend fun performSso(forceRefresh: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            try {
                // 走到这里说明用户主动要用 AI课堂（体检走的是 probeSession，不经过这里）
                markFifUsed()
                if (!forceRefresh && isSessionValid()) return@withContext Result.success(Unit)
                if (forceRefresh) {
                    context.fifDataStore.edit { it.clear() }
                    cookieJar.clear()
                }
                runSsoChain()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception("AI课堂登录失败: ${e.message}", e))
            }
        }
    }

    /**
     * 主动会话体检：跑完整的 SSO 链路，但**不预清本地会话**。
     *
     * 不能用 `performSso(forceRefresh = true)` 当探针 —— 那个会先清 DataStore 与 Cookie，
     * 探测时一旦断网，用户就真的被自己踢下线了。这里失败只返回 failure，本地现状原样保留。
     */
    suspend fun probeSession(): Result<Unit> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            try {
                runSsoChain()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception("AI课堂会话体检失败: ${e.message}", e))
            }
        }
    }

    /**
     * SSO 四步链路的实体。不判缓存、不清会话，由调用方决定前后是否加条件。
     *
     * 1. 携带校内 token 请求 aiitpass.fifedu.com
     * 2. 从 302 Location 提取 FIF token
     * 3. 跟随重定向建立 Cookie 会话
     * 4. 调用 getAiktUserIdByMemberId 获取 FIF 用户标识
     */
    private suspend fun runSsoChain(): Result<Unit> {
        val account = tokenManager.account()
        val credentials = tokenManager.snapshot()
        accountSession.requireCurrent(account.lease)
        val accessToken = credentials.accessToken.orEmpty()
        val userCode = credentials.userCode.orEmpty()
        val userName = credentials.userName.orEmpty()
        val userType = credentials.userType.orEmpty()

        if (accessToken.isBlank() || userCode.isBlank()) {
            return Result.failure(Exception("校内登录信息已失效，请重新登录"))
        }

        // Step 1: SSO 跳转，提取 FIF token
        val fifToken = performSsoRedirect(accessToken, userCode, userName, userType)
            ?: return Result.failure(Exception("FIF 单点登录失败"))
        currentCoroutineContext().ensureActive()
        accountSession.requireCurrent(account.lease)

        // Step 2: 用 token 访问 FIF 首页建立 Cookie
        establishSession(fifToken)

        // Step 3: 用户映射
        val userInfo = fetchUserMapping(fifToken)
        val memberUserId = extractMemberUserIdFromFifToken(fifToken)

        // 持久化
        currentCoroutineContext().ensureActive()
        tokenManager.withCurrentAccount(account.lease) {
            context.fifDataStore.edit { prefs ->
                prefs[KEY_FIF_TOKEN] = fifToken
                prefs[KEY_STUDENT_ID] = userInfo.studentId
                prefs[KEY_USER_NAME] = userInfo.userName
                prefs[KEY_SCHOOL_ID] = userInfo.schoolId
                if (!memberUserId.isNullOrBlank()) {
                    prefs[KEY_MEMBER_USER_ID] = memberUserId
                } else {
                    prefs.remove(KEY_MEMBER_USER_ID)
                }
            }
            // Keep identity publication serialized with logout/account replacement.
            saveIdentityCookies(userInfo.studentId, userInfo.userName)
        }

        return Result.success(Unit)
    }

    private fun performSsoRedirect(
        accessToken: String,
        userCode: String,
        userName: String,
        userType: String,
    ): String? {
        val encodedName = URLEncoder.encode(userName, "UTF-8")
        val url = buildString {
            append(ApiConstants.BASE_FIF_SSO)
            append("/iplat-pass-aiit/h5/login")
            append("?access_token=$accessToken")
            append("&_userCode=$userCode")
            append("&code=$userCode")
            append("&userCode=$userCode")
            append("&_userName=$encodedName")
            append("&_userType=$userType")
            append("&appId=${ApiConstants.APP_ID}")
            append("&returnFromIscToAppFunc=ReturnDefault")
        }

        // 禁止自动重定向以手动提取 Location
        val noRedirectClient = fifClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val request = Request.Builder().url(url).get().build()
        val response = noRedirectClient.newCall(request).execute()

        val location = response.header("Location") ?: return null
        response.close()

        // 从 Location 提取 token 参数
        // e.g. https://sttp.fifedu.com/studycenter-nh5/?token=xxx&app=axx&redirect_uri=null
        return try {
            location.toHttpUrl().queryParameter("token")
        } catch (_: Exception) {
            // URL 解析失败时用正则兜底
            Regex("[?&]token=([^&]+)").find(location)?.groupValues?.get(1)
        }
    }

    /**
     * Step 2: 访问 FIF 首页以建立 SESSION Cookie。
     */
    private fun establishSession(token: String) {
        val url = "${ApiConstants.BASE_FIF}/studycenter-nh5/?token=$token&app=axx"
        val request = Request.Builder().url(url).get().build()
        fifClient.newCall(request).execute().close()
    }

    /**
     * 将 SSO 拿到的身份信息写回 OkHttp cookie jar，
     * 让后续原生请求（如 qrcodeHandler）能从 jar 中拼出完整的 Cookie 头。
     */
    private fun saveIdentityCookies(studentId: String, userName: String) {
        val fifUrl = ApiConstants.BASE_FIF.toHttpUrl()
        val cookies = listOfNotNull(
            Cookie.Builder().name("id").value(studentId).domain("fifedu.com").path("/").build().takeIf { studentId.isNotBlank() },
            Cookie.Builder().name("studentId").value(studentId).domain("fifedu.com").path("/").build().takeIf { studentId.isNotBlank() },
            Cookie.Builder().name("currentUserName").value(userName).domain("fifedu.com").path("/").build().takeIf { userName.isNotBlank() },
        )
        if (cookies.isNotEmpty()) {
            cookieJar.saveFromResponse(fifUrl, cookies)
        }
    }

    /**
     * Step 3: 调用用户映射接口获取 FIF 身份。
     */
    private fun fetchUserMapping(token: String): FifUserInfo {
        val url = "${ApiConstants.BASE_FIF}/studycenter/mobile/common/getAiktUserIdByMemberId"
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.FormBody.Builder().build())
            .header("authorization", "Basic $token")
            .header("Visit-Type", "mobile")
            .build()

        return try {
            fifClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw FifUserMappingException(
                        reason = if (response.code >= 500) FifMappingReason.SERVER else FifMappingReason.UNKNOWN,
                        httpStatus = response.code,
                    )
                }
                parseFifUserMapping(response.body?.string())
            }
        } catch (e: Exception) {
            throw mappingRequestFailure(e)
        }
    }

    /**
     * 构建 FIF 业务请求所需的 authorization header 值。
     */
    suspend fun buildAuthHeader(): String {
        val fifToken = getFifToken().orEmpty()
        return "Basic $fifToken"
    }

    suspend fun appendQrLoginParams(url: String): String {
        val httpUrl = url.toHttpUrl()
        val accessToken = tokenManager.getAccessToken().orEmpty()
        val userCode = tokenManager.getUserCode().orEmpty()
        val userName = tokenManager.getUserName().orEmpty()
        val userType = tokenManager.getUserType().orEmpty()

        if (accessToken.isBlank() || userCode.isBlank()) {
            return url
        }

        return httpUrl.newBuilder().apply {
            if (httpUrl.queryParameter("access_token").isNullOrBlank()) {
                addQueryParameter("access_token", accessToken)
            }
            if (httpUrl.queryParameter("_userCode").isNullOrBlank()) {
                addQueryParameter("_userCode", userCode)
            }
            if (httpUrl.queryParameter("code").isNullOrBlank()) {
                addQueryParameter("code", userCode)
            }
            if (httpUrl.queryParameter("userCode").isNullOrBlank()) {
                addQueryParameter("userCode", userCode)
            }
            if (httpUrl.queryParameter("_userName").isNullOrBlank() && userName.isNotBlank()) {
                addQueryParameter("_userName", userName)
            }
            if (httpUrl.queryParameter("_userType").isNullOrBlank() && userType.isNotBlank()) {
                addQueryParameter("_userType", userType)
            }
            if (httpUrl.queryParameter("appId").isNullOrBlank()) {
                addQueryParameter("appId", ApiConstants.APP_ID)
            }
            if (!httpUrl.queryParameterValues("returnFromIscToAppFunc").contains("ReturnDefault")) {
                addQueryParameter("returnFromIscToAppFunc", "ReturnDefault")
            }
        }.build().toString()
    }

    private suspend fun markFifUsed() {
        context.fifDataStore.edit { if (it[KEY_FIF_USED] != true) it[KEY_FIF_USED] = true }
    }

    suspend fun clear() = sessionMutex.withLock {
        context.fifDataStore.edit { it.clear() }
        cookieJar.clear()
    }
}

internal data class FifUserInfo(val studentId: String, val userName: String, val schoolId: String)

internal fun parseFifUserMapping(body: String?): FifUserInfo {
    try {
        val data = JsonParser.parseString(body.orEmpty()).asJsonObject.getAsJsonObject("data")
            ?: throw FifUserMappingException(FifMappingReason.ACCOUNT_INFO)
        val id = data.get("id")?.takeUnless { it.isJsonNull }?.asString
        if (id.isNullOrBlank()) throw FifUserMappingException(FifMappingReason.ACCOUNT_INFO)
        return FifUserInfo(
            id,
            data.get("userName")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            data.get("schoolId")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        )
    } catch (e: FifUserMappingException) {
        throw e
    } catch (e: Exception) {
        throw FifUserMappingException(FifMappingReason.ACCOUNT_INFO, cause = e)
    }
}

internal fun extractMemberUserIdFromFifToken(token: String): String? {
    val payload = token.split('.').getOrNull(1) ?: return null
    return runCatching {
        val decoded = Base64.getUrlDecoder().decode(payload.padBase64Url())
        val payloadJson = String(decoded, StandardCharsets.UTF_8)
        val jsonObject = JsonParser.parseString(payloadJson).asJsonObject
        jsonObject.get("memberId")?.asString
            ?: jsonObject.get("memberUserId")?.asString
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun String.padBase64Url(): String {
    val remainder = length % 4
    return if (remainder == 0) this else this + "=".repeat(4 - remainder)
}
