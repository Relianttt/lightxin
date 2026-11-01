package com.lightxin.feature.water.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 趣智校园接口。业务字段按报告 §4.4 逐条对齐，公共字段由 [com.lightxin.core.network.QzxyInterceptor] 补。
 * 参数没有默认值：Retrofit 不认 Kotlin 默认参数。
 */
interface QzxyApi {

    @FormUrlEncoded
    @POST("user/login")
    suspend fun login(
        @Field("telephone") telephone: String,
        @Field("password") password: String,
        @Field("phoneSystem") phoneSystem: String,
        @Field("type") type: String,
        @Field("identifier") identifier: String,
        @Field("version") version: String,
    ): QzxyLoginResponse

    @GET("user/verification/code/get")
    suspend fun requestSmsCode(
        @Query("telephone") telephone: String,
        @Query("typeId") typeId: String,
        @Query("platform") platform: String,
        @Query("secret") secret: String,
    ): QzxySimpleResponse

    @FormUrlEncoded
    @POST("user/registerAndLogin")
    suspend fun loginWithSmsCode(
        @Field("telephone") telephone: String,
        @Field("smsCode") smsCode: String,
        @Field("type") type: String,
        @Field("phoneSystem") phoneSystem: String,
        @Field("version") version: String,
    ): QzxyLoginResponse

    @GET("account/wallet")
    suspend fun wallet(): QzxyWalletResponse

    @GET("device/info/mac")
    suspend fun deviceByMac(
        @Query("macAddress") macAddress: String,
        @Query("isNew") isNew: String,
        @Query("isHaier") isHaier: String,
    ): QzxyDeviceResponse

    @FormUrlEncoded
    @POST("order/downRate/bluetooth/rateOrder")
    suspend fun rateOrder(
        @Field("deviceId") deviceId: String,
        @Field("macAddress") macAddress: String,
        @Field("macType") macType: String,
        @Field("bigTypeId") bigTypeId: String,
        @Field("smallTypeId") smallTypeId: String,
        @Field("protocolType") protocolType: String,
        @Field("randomNumber") randomNumber: String,
        @Field("xfModel") xfModel: String,
        @Field("signature") signature: String,
    ): QzxyRateOrderResponse

    @FormUrlEncoded
    @POST("order/upload/bluetooth/data")
    suspend fun uploadData(
        @Field("protocolType") protocolType: String,
        @Field("randomNumber") randomNumber: String,
        @Field("xfData") xfData: String,
        @Field("signature") signature: String,
    ): QzxyUploadDataResponse

    @FormUrlEncoded
    @POST("order/upload/bluetooth/fail")
    suspend fun uploadFail(
        @Field("consumeDate") consumeDate: String,
        @Field("signature") signature: String,
    ): QzxySimpleResponse
}
