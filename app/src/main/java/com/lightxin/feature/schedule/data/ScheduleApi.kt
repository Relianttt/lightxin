package com.lightxin.feature.schedule.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ScheduleApi {

    @FormUrlEncoded
    @POST("dict/selectZcList.do")
    suspend fun getWeekList(
        @Field("_") dummy: String = "",
    ): WeekListResponse

    @FormUrlEncoded
    @POST("mobile/selectStuSelfTimeTable.do")
    suspend fun getTimeTable(
        @Field("userCode") userCode: String,
        @Field("xn") schoolYear: String,
        @Field("xq") schoolTerm: String,
        @Field("zc") week: String,
    ): TimeTableResponse
}
