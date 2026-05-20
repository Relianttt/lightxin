package com.lightxin.feature.update.data

import retrofit2.http.GET

interface GitHubReleaseApi {
    @GET("repos/Relianttt/lightxin/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseDto
}
