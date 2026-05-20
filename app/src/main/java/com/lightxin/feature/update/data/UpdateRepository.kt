package com.lightxin.feature.update.data

import com.lightxin.core.settings.UpdatePrefs
import com.lightxin.feature.update.domain.AppVersion
import com.lightxin.feature.update.domain.AppVersionProvider
import com.lightxin.feature.update.domain.UpdateInfo
import com.lightxin.feature.update.domain.UpdateResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val api: GitHubReleaseApi,
    private val prefs: UpdatePrefs,
    private val versionProvider: AppVersionProvider,
) {
    companion object {
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }

    suspend fun checkForUpdate(force: Boolean): UpdateResult {
        if (!force) {
            val lastAttempt = prefs.lastCheckAttemptTime.first()
            if (System.currentTimeMillis() - lastAttempt < CHECK_INTERVAL_MS) {
                return UpdateResult.Throttled
            }
        }

        val localVersion = versionProvider.appVersion
            ?: return UpdateResult.Failed("无法解析当前版本号")

        prefs.updateCheckAttemptTime()

        val dto = try {
            api.getLatestRelease()
        } catch (e: Exception) {
            return UpdateResult.Failed(e.message ?: "网络请求失败")
        }

        val remoteVersion = AppVersion.parse(dto.tagName)
            ?: return UpdateResult.Failed("无法解析远程版本号")

        val apkAsset = dto.assets?.find { it.name == "app-release.apk" }
            ?: return UpdateResult.Failed("未找到安装包")

        return if (remoteVersion > localVersion) {
            val info = UpdateInfo(
                version = remoteVersion,
                versionName = remoteVersion.toVersionName(),
                downloadUrl = apkAsset.browserDownloadUrl,
                releaseNotes = dto.body.orEmpty(),
            )
            prefs.saveUpdateInfo(info.versionName, info.downloadUrl, info.releaseNotes)
            UpdateResult.HasUpdate(info)
        } else {
            prefs.clearUpdateInfo()
            UpdateResult.AlreadyLatest
        }
    }

    suspend fun getDownloadUrl(): String = prefs.downloadUrl.first()
}
