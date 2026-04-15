package com.lightxin.feature.login.data

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    val data: TokenData?,
    val flag: Boolean?,
    val msg: String?,
)

data class TokenData(
    val token: TokenInfo?,
)

data class TokenInfo(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    val userCode: String?,
    val userName: String?,
    val userType: String?,
    val fileAddress: String?,
)
