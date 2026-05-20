package com.lightxin.feature.update.domain

data class UpdateInfo(
    val version: AppVersion,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
)
