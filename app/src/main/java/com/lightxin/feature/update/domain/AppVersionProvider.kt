package com.lightxin.feature.update.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppVersionProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    val versionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }

    val appVersion: AppVersion? = AppVersion.parse(versionName)
}
