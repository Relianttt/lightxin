package com.lightxin.core.network

import com.lightxin.core.auth.QzxySession
import com.lightxin.core.auth.QzxySessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 趣智校园的公共参数注入。
 *
 * 只挂在本模块专用的 OkHttpClient 上，**不能**复用主 client —— `AuthInterceptor` 会往
 * Form 里塞学校接口的 `access_token` 等字段，污染趣智请求。
 */
@Singleton
class QzxyInterceptor @Inject constructor(
    private val sessionStore: QzxySessionStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val session = runBlocking { sessionStore.current() }
        val path = original.url.encodedPath

        val builder = original.newBuilder().header(HEADER_USER_AGENT, USER_AGENT)
        if (session != null) {
            builder.header(HEADER_CONFIG_PROJECT, session.projectId)
            builder.header(HEADER_CONFIG_KEYS, ApiConstants.QZXY_CONFIG_KEYS)
        }

        // 登录与验证码接口不注入公共参数（此时也还没有会话）
        if (session == null || NO_COMMON_PARAM_PATHS.any { path.endsWith(it) }) {
            return chain.proceed(builder.build())
        }

        val common = commonParams(session)
        val body = original.body
        val request = when {
            original.method == "GET" -> {
                val url = original.url
                val existing = url.queryParameterNames.map { it to url.queryParameter(it).orEmpty() }
                val urlBuilder = url.newBuilder().encodedQuery(null)
                mergeCommon(existing, common).forEach { (name, value) ->
                    urlBuilder.addQueryParameter(name, value)
                }
                builder.url(urlBuilder.build()).build()
            }

            body is FormBody -> {
                val existing = (0 until body.size).map { body.name(it) to body.value(it) }
                val form = FormBody.Builder()
                mergeCommon(existing, common).forEach { (name, value) -> form.add(name, value) }
                builder.method(original.method, form.build()).build()
            }

            else -> builder.build()
        }
        return chain.proceed(request)
    }

    companion object {
        const val USER_AGENT = "okhttp/4.2.2"
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_CONFIG_PROJECT = "Config-Project"
        const val HEADER_CONFIG_KEYS = "Config-Keys"

        private val NO_COMMON_PARAM_PATHS = listOf(
            "/user/login",
            "/user/verification/code/get",
            "/user/registerAndLogin",
        )

        /** 每个请求都要带的字段（报告 §3.3）。 */
        fun commonParams(session: QzxySession): Map<String, String> = linkedMapOf(
            "loginCode" to session.loginCode,
            "userId" to session.userId,
            "accountId" to session.accountId,
            "projectId" to session.projectId,
            "telephone" to session.telephone,
            "telPhone" to session.telephone,
            "phoneSystem" to "android",
            "version" to ApiConstants.QZXY_VERSION,
        )

        /** 已存在的同名字段不覆盖。 */
        fun mergeCommon(
            existing: List<Pair<String, String>>,
            common: Map<String, String>,
        ): List<Pair<String, String>> {
            val taken = existing.mapTo(HashSet()) { it.first }
            val merged = existing.toMutableList()
            for ((name, value) in common) {
                if (name !in taken) merged += name to value
            }
            return merged
        }
    }
}
