package com.lightxin.feature.exam.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ExamApi {

    @FormUrlEncoded
    @POST("mobile/stuExamScore.do")
    suspend fun getExamScores(
        @Field("userCode") userCode: String,
        @Field("kkxn") schoolYear: String,
        @Field("kkxq") semester: String,
    ): ExamScoreResponse

    @FormUrlEncoded
    @POST("mobile/mobileXsxkKkxns.do")
    suspend fun getSchoolYears(
        @Field("_") dummy: String = "",
    ): SchoolYearResponse

    @FormUrlEncoded
    @POST("mobile/getXlVo.do")
    suspend fun getCurrentTerm(
        @Field("_") dummy: String = "",
    ): CurrentTermResponse
}
