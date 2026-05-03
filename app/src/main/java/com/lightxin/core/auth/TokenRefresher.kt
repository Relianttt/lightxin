package com.lightxin.core.auth

interface TokenRefresher {
    suspend fun refreshToken(): Boolean
}
