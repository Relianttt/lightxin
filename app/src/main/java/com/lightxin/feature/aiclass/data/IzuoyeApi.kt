package com.lightxin.feature.aiclass.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface IzuoyeApi {

    @FormUrlEncoded
    @POST("kyxl-homework/oral/cw/public/getTeaWorkDetail")
    suspend fun getTeaWorkDetail(
        @Field("schoolId") schoolId: String,
        @Field("userId") userId: String,
        @Field("teaCwId") teaCwId: String,
        @Field("uid") uid: String,
        @Field("classId") classId: String,
        @Field("studentId") studentId: String,
        @Header("jtzy") jtzy: String,
    ): IzuoyeTeaWorkDetailResponse

    @FormUrlEncoded
    @POST("kyxl-homework/oral/cw/public/getStuWorkList")
    suspend fun getStuWorkList(
        @Field("schoolId") schoolId: String,
        @Field("userId") userId: String,
        @Field("teaCwId") teaCwId: String,
        @Field("cwType") cwType: String = "3",
        @Field("cwStatus") cwStatus: String = "2",
        @Field("classId") classId: String,
        @Field("studentId") studentId: String,
        @Field("currentPage") currentPage: String = "1",
        @Field("pageSize") pageSize: String = "10",
        @Field("viewPermit") viewPermit: String = "1",
        @Field("cwDeadline") cwDeadline: String,
        @Field("correctStatus") correctStatus: String = "",
        @Header("jtzy") jtzy: String,
    ): IzuoyeStuWorkListResponse

    @FormUrlEncoded
    @POST("kyxl-homework/oral/cw/public/getStuPersonOrGroupWorkDetail")
    suspend fun getStuWorkDetail(
        @Field("schoolId") schoolId: String,
        @Field("stuCwId") stuCwId: String,
        @Header("jtzy") jtzy: String,
    ): IzuoyeStuWorkDetailResponse

    @FormUrlEncoded
    @POST("kyxl-homework/oral/cw/public/getStorageId")
    suspend fun getStorageId(
        @Field("schoolId") schoolId: String,
        @Field("userId") userId: String,
        @Field("teaCwId") teaCwId: String,
        @Field("classId") classId: String,
        @Header("jtzy") jtzy: String,
    ): IzuoyeStorageIdResponse

    @FormUrlEncoded
    @POST("kyxl-homework/oral/cw/stu/submitWork")
    suspend fun submitWork(
        @Field("schoolId") schoolId: String,
        @Field("userId") userId: String,
        @Field("teaCwId") teaCwId: String,
        @Field("classId") classId: String,
        @Field("stuCwId") stuCwId: String,
        @Field("showContent") showContent: String,
        @Header("jtzy") jtzy: String,
    ): IzuoyeBaseResponse
}
