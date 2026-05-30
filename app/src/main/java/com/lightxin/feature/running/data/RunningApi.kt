package com.lightxin.feature.running.data

import com.google.gson.JsonObject
import com.lightxin.core.network.SportsRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ResponseBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import javax.inject.Singleton

interface RunningApi {

    @FormUrlEncoded
    @POST("mobile/sportApp/startIndex.do")
    suspend fun getStartIndex(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/sportApp/checkDsStudent.do")
    suspend fun checkDsStudent(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/index/clubInfo.do")
    suspend fun getClubInfo(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/index/extraInfo.do")
    suspend fun getExtraInfo(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/extra/extraDetailInfo.do")
    suspend fun getExtraDetailInfo(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/auto/clubInfo.do")
    suspend fun getClubDetail(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @Multipart
    @POST("mobile/qrcode/getQrcodeResult.do")
    suspend fun getQrcodeResult(
        @Part("studentCode") studentCode: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
    ): JsonObject

    @FormUrlEncoded
    @POST("mobile/sportApp/startRunning.do")
    suspend fun startRunning(
        @FieldMap params: Map<String, String>,
    ): JsonObject

    @GET("mobile/time/getServerTime.do")
    suspend fun getServerTime(): ResponseBody

    @FormUrlEncoded
    @POST("mobile/extra/addExtraCheckNew.do")
    suspend fun uploadRunningRecord(
        @Field("list") list: String,
    ): JsonObject
}

@Module
@InstallIn(SingletonComponent::class)
object RunningModule {

    @Provides
    @Singleton
    fun provideRunningApi(@SportsRetrofit retrofit: Retrofit): RunningApi =
        retrofit.create(RunningApi::class.java)
}
