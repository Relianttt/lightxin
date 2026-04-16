package com.lightxin.core.auth

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val tokenManager: TokenManager,
) {
    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn
    val isOnboarded: Flow<Boolean> = tokenManager.isOnboarded

    suspend fun markOnboarded() {
        tokenManager.markOnboarded()
    }

    suspend fun logout() {
        tokenManager.clear()
    }
}
