package com.lightxin.feature.labor.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface LaborApi {

    @FormUrlEncoded
    @POST("anXiaoXin/queryPersonalTimesTotal.do")
    suspend fun queryHoursTotal(
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
        @Field("accessToken") accessToken: String,
    ): HoursTotalResponse

    @FormUrlEncoded
    @POST("anXiaoXin/queryTimesDetailsPage.do")
    suspend fun queryActivities(
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
        @Field("accessToken") accessToken: String,
        @Field("pageNo") pageNo: String,
        @Field("pageSize") pageSize: String,
    ): ActivityPageResponse

    @FormUrlEncoded
    @POST("anXiaoXin/queryTimesDetails.do")
    suspend fun queryActivityDetail(
        @Field("userCode") userCode: String,
        @Field("xh") xh: String,
        @Field("accessToken") accessToken: String,
        @Field("id") id: String,
        @Field("type") type: String,
    ): ActivityDetailResponse
}
