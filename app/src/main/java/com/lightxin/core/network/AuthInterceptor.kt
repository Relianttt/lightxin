package com.lightxin.core.network

import com.lightxin.core.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 根据请求域名自动注入对应的认证参数。
 *
 * - 主站 (in.aiit.edu.cn): Form表单追加通用认证参数
 * - 查寝 (fdygl.aiit.edu.cn): JSON body，不由Interceptor注入（在Api层处理）
 * - 运动 (sports.aiit.edu.cn): studentCode header
 * - 劳动 (ldjy.aiit.edu.cn): Form表单追加 userCode/accessToken
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        val path = original.url.encodedPath

        val request = when {
            // 登录接口不需要注入认证参数
            path.contains("getToken.do") || path.contains("refresh.do") -> original

            // 运动接口: header注入 studentCode
            host.contains("sports.aiit.edu.cn") -> {
                val userCode = runBlocking { tokenManager.getUserCode() } ?: ""
                original.newBuilder()
                    .header("studentCode", userCode)
                    .build()
            }

            // 查寝接口: 认证在Api层通过JSON body处理，Interceptor只加通用header
            host.contains("fdygl.aiit.edu.cn") -> {
                val token = runBlocking { tokenManager.getAccessToken() } ?: ""
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }

            // 主站 & 劳动教育: Form表单追加通用参数
            else -> {
                val token = runBlocking { tokenManager.getAccessToken() } ?: ""
                val userCode = runBlocking { tokenManager.getUserCode() } ?: ""
                val userName = runBlocking { tokenManager.getUserName() } ?: ""
                val userType = runBlocking { tokenManager.getUserType() } ?: "1"
                val encodedName = URLEncoder.encode(userName, "UTF-8")

                val body = original.body
                if (body is FormBody) {
                    val newBody = FormBody.Builder()
                    for (i in 0 until body.size) {
                        newBody.add(body.name(i), body.value(i))
                    }
                    newBody.add("access_token", token)
                    newBody.add("_userCode", userCode)
                    newBody.add("userId", userCode)
                    newBody.add("_userName", encodedName)
                    newBody.add("_userType", userType)
                    newBody.add("appId", ApiConstants.APP_ID)

                    // 劳动教育额外字段
                    if (host.contains("ldjy.aiit.edu.cn")) {
                        newBody.add("userCode", userCode)
                        newBody.add("xh", userCode)
                        newBody.add("userName", userName)
                        newBody.add("accessToken", token)
                    }

                    original.newBuilder().post(newBody.build()).build()
                } else {
                    original
                }
            }
        }

        return chain.proceed(request)
    }
}
