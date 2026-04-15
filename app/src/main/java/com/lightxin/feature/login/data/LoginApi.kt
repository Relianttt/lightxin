package com.lightxin.feature.login.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface LoginApi {

    @FormUrlEncoded
    @POST("mobile/getToken.do")
    suspend fun login(
        @Field("userCode") userCode: String,
        @Field("password") password: String,
    ): LoginResponse

    @FormUrlEncoded
    @POST("mobile/refresh.do")
    suspend fun refreshToken(
        @Field("refresh_token") refreshToken: String,
    ): LoginResponse
}
