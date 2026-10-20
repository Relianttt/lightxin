package com.lightxin.feature.login.data

import com.lightxin.core.auth.RSAUtils
import com.lightxin.core.auth.AccountSession
import kotlinx.coroutines.CancellationException
import com.lightxin.core.auth.TokenManager
import com.lightxin.core.auth.TokenRefresher
import com.lightxin.core.network.AuthRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginRepository @Inject constructor(
    private val api: LoginApi,
    private val tokenManager: TokenManager,
    private val accountSession: AccountSession,
) : TokenRefresher {
    suspend fun login(userCode: String, password: String): Result<Unit> {
        return try {
            val encryptedPassword = RSAUtils.encryptPassword(password)
            val response = api.login(userCode, encryptedPassword)
            val token = response.data?.token
            val accessToken = token?.accessToken

            if (token == null || accessToken.isNullOrBlank()) {
                Result.failure(Exception(response.msg ?: "登录失败"))
            } else {
                tokenManager.saveLoginData(
                    accessToken = accessToken,
                    refreshToken = token.refreshToken ?: "",
                    userCode = token.userCode ?: userCode,
                    userName = token.userName ?: "",
                    userType = token.userType ?: "1",
                    fileAddress = token.fileAddress ?: "",
                )
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshToken(): Boolean {
        return try {
            val lease = accountSession.capture()
            val refreshToken = tokenManager.getRefreshToken() ?: return false
            val response = api.refreshToken(refreshToken)
            val token = response.data?.token
            val accessToken = token?.accessToken

            if (token == null || accessToken.isNullOrBlank()) return false

            tokenManager.updateTokens(lease, accessToken, token.refreshToken ?: refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LoginModule {

    @Provides
    @Singleton
    fun provideLoginApi(@AuthRetrofit retrofit: Retrofit): LoginApi =
        retrofit.create(LoginApi::class.java)

    @Provides
    @Singleton
    fun provideTokenRefresher(repository: LoginRepository): TokenRefresher =
        repository
}
