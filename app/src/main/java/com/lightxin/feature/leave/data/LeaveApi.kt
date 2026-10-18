package com.lightxin.feature.leave.data

import com.google.gson.JsonObject
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import javax.inject.Singleton
import com.lightxin.core.network.LeaveRetrofit

/**
 * 请假工作流（ywlz/zhxy-bfc）。
 *
 * 服务端返回结构在不同端点间不稳定，统一以 JsonObject 接收，由 Repository 容错提取。
 * dataJson 必须以字符串二次序列化后作为普通表单字段传递。
 */
interface LeaveApi {

    @FormUrlEncoded
    @POST("mobile/flowRuTask/getFlowDefinitionsplus.do")
    suspend fun getFlowDefinitions(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/process/getFlowSheet.do")
    suspend fun getFlowSheet(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/process/findDefinitionBase.do")
    suspend fun findDefinitionBase(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/process/saveForm.do")
    suspend fun saveForm(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/process/submitForm.do")
    suspend fun submitForm(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/flowRuTask/myExecution.do")
    suspend fun myExecution(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/process/getForm.do")
    suspend fun getForm(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/process/getMyRequestButtons.do")
    suspend fun getMyRequestButtons(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/flowRuTask/withdrawExecution.do")
    suspend fun withdrawExecution(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("mobile/process/getXjxx.do")
    suspend fun getXjxx(@FieldMap fields: Map<String, String>): JsonObject

    @Multipart
    @POST("file/upload.do")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("userCode") userCode: RequestBody,
        @Part("accessToken") accessToken: RequestBody,
    ): JsonObject
}

@Module
@InstallIn(SingletonComponent::class)
object LeaveModule {
    @Provides
    @Singleton
    fun provideLeaveApi(@LeaveRetrofit retrofit: Retrofit): LeaveApi =
        retrofit.create(LeaveApi::class.java)
}
