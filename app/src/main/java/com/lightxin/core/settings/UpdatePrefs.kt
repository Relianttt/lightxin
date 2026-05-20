package com.lightxin.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "lightxin_update")

@Singleton
class UpdatePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_LAST_CHECK_ATTEMPT_TIME = longPreferencesKey("last_check_attempt_time")
        private val KEY_LATEST_VERSION = stringPreferencesKey("latest_version")
        private val KEY_DOWNLOAD_URL = stringPreferencesKey("download_url")
        private val KEY_RELEASE_NOTES = stringPreferencesKey("release_notes")
        private val KEY_SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        private val KEY_PENDING_DOWNLOAD_ID = longPreferencesKey("pending_download_id")
    }

    val lastCheckAttemptTime: Flow<Long> = context.updateDataStore.data.map { it[KEY_LAST_CHECK_ATTEMPT_TIME] ?: 0L }
    val latestVersion: Flow<String> = context.updateDataStore.data.map { it[KEY_LATEST_VERSION].orEmpty() }
    val downloadUrl: Flow<String> = context.updateDataStore.data.map { it[KEY_DOWNLOAD_URL].orEmpty() }
    val releaseNotes: Flow<String> = context.updateDataStore.data.map { it[KEY_RELEASE_NOTES].orEmpty() }
    val skippedVersion: Flow<String> = context.updateDataStore.data.map { it[KEY_SKIPPED_VERSION].orEmpty() }
    val pendingDownloadId: Flow<Long> = context.updateDataStore.data.map { it[KEY_PENDING_DOWNLOAD_ID] ?: -1L }

    suspend fun saveUpdateInfo(versionName: String, url: String, notes: String) {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_LATEST_VERSION] = versionName
            prefs[KEY_DOWNLOAD_URL] = url
            prefs[KEY_RELEASE_NOTES] = notes
            prefs[KEY_LAST_CHECK_ATTEMPT_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun clearUpdateInfo() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(KEY_LATEST_VERSION)
            prefs.remove(KEY_DOWNLOAD_URL)
            prefs.remove(KEY_RELEASE_NOTES)
            prefs[KEY_LAST_CHECK_ATTEMPT_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun updateCheckAttemptTime() {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_LAST_CHECK_ATTEMPT_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun setSkippedVersion(version: String) {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_SKIPPED_VERSION] = version
        }
    }

    suspend fun savePendingDownloadId(id: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_PENDING_DOWNLOAD_ID] = id
        }
    }

    suspend fun clearPendingDownloadId() {
        context.updateDataStore.edit { prefs ->
            prefs.remove(KEY_PENDING_DOWNLOAD_ID)
        }
    }
}
