package com.lightxin.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lightxin_auth")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_CODE = stringPreferencesKey("user_code")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_TYPE = stringPreferencesKey("user_type")
        private val KEY_FILE_ADDRESS = stringPreferencesKey("file_address")
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCESS_TOKEN]?.isNotBlank() == true
    }

    suspend fun saveLoginData(
        accessToken: String,
        refreshToken: String,
        userCode: String,
        userName: String,
        userType: String,
        fileAddress: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_USER_CODE] = userCode
            prefs[KEY_USER_NAME] = userName
            prefs[KEY_USER_TYPE] = userType
            prefs[KEY_FILE_ADDRESS] = fileAddress
        }
    }

    suspend fun getAccessToken(): String? =
        context.dataStore.data.first()[KEY_ACCESS_TOKEN]

    suspend fun getRefreshToken(): String? =
        context.dataStore.data.first()[KEY_REFRESH_TOKEN]

    suspend fun getUserCode(): String? =
        context.dataStore.data.first()[KEY_USER_CODE]

    suspend fun getUserName(): String? =
        context.dataStore.data.first()[KEY_USER_NAME]

    suspend fun getUserType(): String? =
        context.dataStore.data.first()[KEY_USER_TYPE]

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }


}
