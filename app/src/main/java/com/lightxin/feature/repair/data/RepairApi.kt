package com.lightxin.feature.repair.data

import com.google.gson.JsonObject
import com.lightxin.core.network.RepairRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Singleton

/**
 * 报修手机端 bxjlSjd 家族。
 * 注意 timestap 是服务端原样拼写，不要"修正"。
 */
interface RepairApi {

    @FormUrlEncoded
    @POST("bxjlSjd/getWdbxListForSjd.do")
    suspend fun getWdbxList(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/getBxdXqForSjd.do")
    suspend fun getBxdXq(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/getLctByBxdhForSjd.do")
    suspend fun getLct(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/getBxddForSjd.do")
    suspend fun getBxdd(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/getBxlxForSjd.do")
    suspend fun getBxlx(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/getXqByXhForSjd.do")
    suspend fun getXq(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/getSjhForSjd.do")
    suspend fun getSjh(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/saveOrUpdateBxdForSjd.do")
    suspend fun saveOrUpdate(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/updateBxdByIdForSjd.do")
    suspend fun updateBxdById(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/addLcbzForSjd.do")
    suspend fun addLcbz(@FieldMap fields: Map<String, String>): JsonObject

    @FormUrlEncoded
    @POST("bxjlSjd/uploadForSjd.do")
    suspend fun upload(
        @Query("timestap") timestap: Long,
        @Field("fileImg") fileImg: String,
    ): JsonObject
}

@Module
@InstallIn(SingletonComponent::class)
object RepairModule {
    @Provides
    @Singleton
    fun provideRepairApi(@RepairRetrofit retrofit: Retrofit): RepairApi =
        retrofit.create(RepairApi::class.java)
}
