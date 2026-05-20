package com.lightxin.feature.update.data

import com.google.gson.annotations.SerializedName

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    val body: String?,
    val assets: List<AssetDto>?,
)

data class AssetDto(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
)
