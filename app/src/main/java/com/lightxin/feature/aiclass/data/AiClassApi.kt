package com.lightxin.feature.aiclass.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface AiClassApi {

    /** 课程列表 */
    @FormUrlEncoded
    @POST("studycenter/mobile/student/myClassroom")
    suspend fun getCourses(
        @Field("termYear") termYear: String,
        @Field("term") term: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassCourseResponse

    /** 学期列表 */
    @POST("studycenter/mobile/common/termList")
    suspend fun getTermList(
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassTermResponse

    /** 查询当前签到信息 */
    @FormUrlEncoded
    @POST("coursecenter-interaction/sign/getSignInCourseInfo")
    suspend fun getSignInInfo(
        @Field("teachClassId") teachClassId: String,
        @Field("courseRecordId") courseRecordId: String,
        @Field("studentId") studentId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassSignInInfoResponse

    /** 数字码签到 */
    @FormUrlEncoded
    @POST("coursecenter-interaction/qrcodeV2/checkQrcodeHandler")
    suspend fun submitSignCode(
        @Field("signId") signId: String,
        @Field("userName") userName: String,
        @Field("signCode") signCode: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassBaseResponse

    /** 查询当前正在上课记录 */
    @FormUrlEncoded
    @POST("coursecenter-interaction/courseRecord/getWorkingCourseRecordByStudentId")
    suspend fun getWorkingCourseRecord(
        @Field("studentId") studentId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassWorkingRecordResponse

    /** 扫码签到：处理二维码 token（302 跳转，需手动处理） */
    @GET("coursecenter-interaction/qrcodeV2/qrcodeHandler")
    suspend fun qrcodeHandler(
        @Query("token") token: String,
        @Query("openTheWay") openTheWay: String = "2",
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): retrofit2.Response<okhttp3.ResponseBody>

    /** 课程详情里的试卷信息，可能包含历史测验入口数据 */
    @GET("coursecenter-interaction/paper/getPaperInCourseInfo")
    suspend fun getPaperInCourseInfo(
        @Query("courseId") courseId: String,
        @Query("studentId") studentId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassCoursePaperInfoResponse

    /** 学生可见测验列表 */
    @GET("coursecenter-interaction/paper/getPublishPaperListOfStudent")
    suspend fun getPublishPaperListOfStudent(
        @Query("courseId") courseId: String,
        @Query("studentId") studentId: String,
        @Query("userId") userId: String,
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassQuizListResponse

    /** AI课堂课表，用于补齐“我的课程”未返回的当前学期课程 */
    @GET("coursecenter-interaction/liveAndRecord/timetableInfo")
    suspend fun getTimetableInfo(
        @Query("schoolId") schoolId: String,
        @Query("mid") memberId: String,
        @Query("identity") identity: String = "2",
        @Header("authorization") auth: String,
        @Header("Visit-Type") visitType: String = "mobile",
    ): AiClassTimetableResponse
}
