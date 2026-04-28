package com.lightxin.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lightxin.core.auth.TokenManager
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
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
    private val cookieJar: CookieJar,
) {
    companion object {
        private val KEY_FIF_TOKEN = stringPreferencesKey("fif_token")
        private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_SCHOOL_ID = stringPreferencesKey("school_id")
        private val KEY_MEMBER_USER_ID = stringPreferencesKey("member_user_id")
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
    suspend fun performSso(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = tokenManager.getAccessToken().orEmpty()
            val userCode = tokenManager.getUserCode().orEmpty()
            val userName = tokenManager.getUserName().orEmpty()
            val userType = tokenManager.getUserType().orEmpty()

            if (accessToken.isBlank() || userCode.isBlank()) {
                return@withContext Result.failure(Exception("校内登录信息已失效，请重新登录"))
            }

            // Step 1: SSO 跳转，提取 FIF token
            val fifToken = performSsoRedirect(accessToken, userCode, userName, userType)
                ?: return@withContext Result.failure(Exception("FIF 单点登录失败"))

            // Step 2: 用 token 访问 FIF 首页建立 Cookie
            establishSession(fifToken)

            // Step 3: 用户映射
            val userInfo = fetchUserMapping(fifToken)
                ?: return@withContext Result.failure(Exception("FIF 用户映射失败"))
            val memberUserId = extractMemberUserIdFromFifToken(fifToken)

            // 持久化
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

            // 同步身份 Cookie 到 OkHttp jar，让 qrcodeHandler 等原生请求能从 jar 中读取
            saveIdentityCookies(userInfo.studentId, userInfo.userName)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("AI课堂登录失败: ${e.message}", e))
        }
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
    private fun fetchUserMapping(token: String): FifUserInfo? {
        val url = "${ApiConstants.BASE_FIF}/studycenter/mobile/common/getAiktUserIdByMemberId"
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.FormBody.Builder().build())
            .header("authorization", "Basic $token")
            .header("Visit-Type", "mobile")
            .build()

        val response = fifClient.newCall(request).execute()
        val body = response.body?.string() ?: return null
        response.close()

        // 解析 JSON: { data: { id, userType, number, userName, schoolId } }
        return try {
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonObject("data") ?: return null
            FifUserInfo(
                studentId = data.get("id")?.asString ?: return null,
                userName = data.get("userName")?.asString.orEmpty(),
                schoolId = data.get("schoolId")?.asString.orEmpty(),
            )
        } catch (_: Exception) {
            null
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

    suspend fun clear() {
        context.fifDataStore.edit { it.clear() }
        // 清除 FIF 相关 Cookie
        val fifUrl = ApiConstants.BASE_FIF.toHttpUrl()
        cookieJar.loadForRequest(fifUrl) // 触发读取，无法直接清除，但会话失效后不影响
    }

    private data class FifUserInfo(
        val studentId: String,
        val userName: String,
        val schoolId: String,
    )
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
