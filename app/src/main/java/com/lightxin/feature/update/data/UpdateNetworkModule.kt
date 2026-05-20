package com.lightxin.feature.update.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdateRetrofit

@Module
@InstallIn(SingletonComponent::class)
object UpdateNetworkModule {

    @Provides
    @Singleton
    @UpdateRetrofit
    fun provideUpdateRetrofit(): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubReleaseApi(@UpdateRetrofit retrofit: Retrofit): GitHubReleaseApi {
        return retrofit.create(GitHubReleaseApi::class.java)
    }
}
