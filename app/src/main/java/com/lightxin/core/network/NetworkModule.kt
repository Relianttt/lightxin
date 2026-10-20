package com.lightxin.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CshRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CheckinRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SportsRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LaborRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LeaveRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RepairRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CreditRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class FifRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class FifOkHttpClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IzuoyeRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshInterceptor: TokenRefreshInterceptor,
    ): OkHttpClient {

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenRefreshInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides @Singleton @AuthRetrofit
    fun provideAuthRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_AUTH + "/")

    @Provides @Singleton @MainRetrofit
    fun provideMainRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_SCPS + "/")

    @Provides @Singleton @CshRetrofit
    fun provideCshRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_CSH + "/")

    @Provides @Singleton @CheckinRetrofit
    fun provideCheckinRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_CHECKIN + "/")

    @Provides @Singleton @SportsRetrofit
    fun provideSportsRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_SPORTS + "/")

    @Provides @Singleton @LaborRetrofit
    fun provideLaborRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_LABOR + "/")

    @Provides @Singleton @LeaveRetrofit
    fun provideLeaveRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_LEAVE + "/")

    @Provides @Singleton @RepairRetrofit
    fun provideRepairRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_REPAIR + "/")

    // 素质学分：复用主 OkHttpClient，AuthInterceptor 默认分支会向 FormBody 注入
    // access_token/_userCode/userId 等通用字段，cqc.aiit.edu.cn 服务端忽略多余参数。
    // 接口真正需要的 studentCode 由 CreditApi 显式 @Field 声明。
    @Provides @Singleton @CreditRetrofit
    fun provideCreditRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_CREDIT + "/")

    // ─── FIF AI课堂 ───

    @Provides @Singleton
    fun provideFifCookieJar(cookieJar: FifCookieJar): CookieJar = cookieJar

    @Provides @Singleton @FifOkHttpClient
    fun provideFifOkHttpClient(cookieJar: CookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton @FifRetrofit
    fun provideFifRetrofit(@FifOkHttpClient client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_FIF + "/")

    @Provides @Singleton @IzuoyeRetrofit
    fun provideIzuoyeRetrofit(@FifOkHttpClient client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_IZUOYE + "/")
}
