package com.lightxin.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CshRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CheckinRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SportsRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LaborRetrofit
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
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenRefreshInterceptor)
            .addInterceptor(logging)
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

    @Provides @Singleton @CreditRetrofit
    fun provideCreditRetrofit(client: OkHttpClient): Retrofit =
        buildRetrofit(client, ApiConstants.BASE_CREDIT + "/")

    // ─── FIF AI课堂 ───

    @Provides @Singleton
    fun provideFifCookieJar(): CookieJar {
        val store = ConcurrentHashMap<String, List<Cookie>>()
        return object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return store.values.flatten().filter { it.matches(url) }
            }
        }
    }

    @Provides @Singleton @FifOkHttpClient
    fun provideFifOkHttpClient(cookieJar: CookieJar): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(logging)
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
