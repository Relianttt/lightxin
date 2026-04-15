package com.lightxin.core.network

import com.lightxin.feature.login.data.LoginRepository
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当收到401响应时，尝试用 refresh_token 刷新 access_token 并重试请求。
 * 通过 Lazy 注入 LoginRepository 避免循环依赖。
 */
@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val loginRepository: dagger.Lazy<LoginRepository>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401) {
            val refreshed = kotlinx.coroutines.runBlocking {
                loginRepository.get().refreshToken()
            }
            if (refreshed) {
                response.close()
                return chain.proceed(chain.request())
            }
        }

        return response
    }
}
