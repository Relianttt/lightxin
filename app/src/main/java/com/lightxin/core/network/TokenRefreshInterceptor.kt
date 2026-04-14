package com.lightxin.core.network

import com.lightxin.core.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当收到401响应时，尝试用 refresh_token 刷新 access_token 并重试请求。
 */
@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401) {
            val refreshed = runBlocking { tokenManager.refreshToken() }
            if (refreshed) {
                response.close()
                return chain.proceed(chain.request())
            }
        }

        return response
    }
}
