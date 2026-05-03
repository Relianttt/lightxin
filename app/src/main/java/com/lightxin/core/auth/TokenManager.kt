package com.lightxin.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

data class AuthCredentials(
    val accessToken: String?,
    val refreshToken: String?,
    val userCode: String?,
    val userName: String?,
    val userType: String?,
    val fileAddress: String?,
)

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
        private val KEY_ONBOARDED = booleanPreferencesKey("lxin_onboarded")

        // 退出登录时应被清除的 key（排除 KEY_ONBOARDED —— 欢迎页只在首启触发）
        private val SESSION_KEYS = listOf(
            KEY_ACCESS_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_USER_CODE,
            KEY_USER_NAME,
            KEY_USER_TYPE,
            KEY_FILE_ADDRESS,
        )
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCESS_TOKEN]?.isNotBlank() == true
    }

    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDED] == true
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

    suspend fun markOnboarded() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDED] = true
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

    suspend fun snapshot(): AuthCredentials {
        val prefs = context.dataStore.data.first()
        return AuthCredentials(
            accessToken = prefs[KEY_ACCESS_TOKEN],
            refreshToken = prefs[KEY_REFRESH_TOKEN],
            userCode = prefs[KEY_USER_CODE],
            userName = prefs[KEY_USER_NAME],
            userType = prefs[KEY_USER_TYPE],
            fileAddress = prefs[KEY_FILE_ADDRESS],
        )
    }

    /** 仅清除会话相关 key；onboarded 标志保留，避免退出登录后重现欢迎页。 */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            SESSION_KEYS.forEach { prefs.remove(it) }
        }
    }
}
