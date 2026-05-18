package com.lightxin.feature.credit.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface CreditApi {

    @FormUrlEncoded
    @POST("creditMobile/findPersonCredit.do")
    suspend fun getPersonCredit(
        @Field("studentCode") studentCode: String,
    ): CreditOverviewResponse

    @FormUrlEncoded
    @POST("creditMobile/findCreditRecord.do")
    suspend fun getCreditRecords(
        @Field("studentCode") studentCode: String,
    ): CreditRecordListResponse

    @FormUrlEncoded
    @POST("creditMobile/findCreditRecordDetail.do")
    suspend fun getCreditRecordDetail(
        @Field("id") id: String,
        @Field("studentCode") studentCode: String,
    ): CreditRecordDetailResponse
}
