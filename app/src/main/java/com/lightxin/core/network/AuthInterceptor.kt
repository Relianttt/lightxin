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
                val credentials = runBlocking { tokenManager.snapshot() }
                val token = credentials.accessToken.orEmpty()
                val userCode = credentials.userCode.orEmpty()
                val userType = credentials.userType ?: "1"
                original.newBuilder()
                    .header("studentCode", userCode)
                    .header("accessToken", token)
                    .header("access_token", token)
                    .header("userCode", userCode)
                    .header("xh", userCode)
                    .header("_userCode", userCode)
                    .header("_userType", userType)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", "http://sports.aiit.edu.cn:8082")
                    .header("Referer", "http://sports.aiit.edu.cn:8082/dist/")
                    .build()
            }

            // 查寝接口: H5 实际请求会携带 accessToken/userCode/userType/xh 等身份头
            host.contains("fdygl.aiit.edu.cn") -> {
                val credentials = runBlocking { tokenManager.snapshot() }
                val token = credentials.accessToken.orEmpty()
                val userCode = credentials.userCode.orEmpty()
                val userType = credentials.userType ?: "1"
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("accessToken", token)
                    .header("access_token", token)
                    .header("userCode", userCode)
                    .header("xh", userCode)
                    .header("userType", userType)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", "https://fdygl.aiit.edu.cn")
                    .header("Referer", "https://fdygl.aiit.edu.cn/swp-app/")
                    .build()
            }

            // 请假工作流 (ywlz): 只带 accessToken/userCode/xh/_userCode/_userType
            host.contains("ywlz.aiit.edu.cn") -> {
                val credentials = runBlocking { tokenManager.snapshot() }
                val token = credentials.accessToken.orEmpty()
                val userCode = credentials.userCode.orEmpty()
                val userType = credentials.userType ?: "1"
                val body = original.body
                val builder = original.newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", "https://ywlz.aiit.edu.cn")
                    .header("Referer", "https://ywlz.aiit.edu.cn/mobile/")
                if (body is FormBody) {
                    val rebuilt = FormBody.Builder().apply {
                        for (i in 0 until body.size) add(body.name(i), body.value(i))
                        add("accessToken", token)
                        add("userCode", userCode)
                        add("xh", userCode)
                        add("_userCode", userCode)
                        add("_userType", userType)
                    }.build()
                    builder.post(rebuilt)
                }
                builder.build()
            }

            // 报修手机端 (repair): Form表单追加身份字段，不加 appId/access_token
            host.contains("repair.aiit.edu.cn") -> {
                val credentials = runBlocking { tokenManager.snapshot() }
                val token = credentials.accessToken.orEmpty()
                val userCode = credentials.userCode.orEmpty()
                val userName = credentials.userName.orEmpty()
                val userType = credentials.userType ?: "1"
                val body = original.body
                val builder = original.newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", "http://repair.aiit.edu.cn:8081")
                    .header("Referer", "http://repair.aiit.edu.cn:8081/dist-app/")
                if (body is FormBody) {
                    val rebuilt = FormBody.Builder().apply {
                        for (i in 0 until body.size) add(body.name(i), body.value(i))
                        add("userCode", userCode)
                        add("xh", userCode)
                        add("userType", userType)
                        add("userName", userName)
                        add("accessToken", token)
                    }.build()
                    builder.post(rebuilt)
                }
                builder.build()
            }

            // 主站 & 劳动教育: Form表单追加通用参数
            else -> {
                val credentials = runBlocking { tokenManager.snapshot() }
                val token = credentials.accessToken.orEmpty()
                val userCode = credentials.userCode.orEmpty()
                val userName = credentials.userName.orEmpty()
                val userType = credentials.userType ?: "1"
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
