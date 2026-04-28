package com.lightxin.feature.holiday.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 节假日离返校登记接口 (fdygl.aiit.edu.cn)
 * 复用 @CheckinRetrofit，认证通过 AuthInterceptor fdygl 档注入。
 */
interface HolidayApi {

    /** 获取登记任务列表（分页） */
    @POST("app/holiday/getRegistrationPage")
    suspend fun getRegistrationPage(
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): RegistrationPageResponse

    /** 获取节假日详细配置 */
    @GET("app/holiday/getHolidaySetById")
    suspend fun getHolidaySetById(
        @Query("holidayId") holidayId: String,
    ): HolidayDetailResponse

    /** 查询字典（离校/留校等选项） */
    @POST("app/dict/list")
    suspend fun listDict(
        @Body body: Map<String, String>,
    ): DictResponse

    /** 获取学生已有登记记录（回填用） */
    @GET("app/holiday/getHolidayRegister")
    suspend fun getHolidayRegister(
        @Query("holidayId") holidayId: String,
        @Query("studentId") studentId: String,
    ): HolidayRegisterResponse

    /** 提交/保存登记 */
    @POST("app/holiday/save")
    suspend fun save(
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): SaveResponse
}
