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

private val Context.qzxyDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "qzxy_session")

/** 趣智校园会话。与学校账号完全独立，退出学校账号不清它。 */
data class QzxySession(
    val telephone: String,
    val loginCode: String,
    val userId: String,
    val accountId: String,
    val projectId: String,
    val name: String,
) {
    val isValid: Boolean get() = loginCode.isNotBlank() && telephone.isNotBlank()
}

/**
 * 趣智会话落盘。不存密码。
 *
 * 官方 App 登录会把本 App 的会话顶掉（报告 §3.4），反之亦然。
 */
@Singleton
class QzxySessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val sessionFlow: Flow<QzxySession?> = context.qzxyDataStore.data.map { prefs ->
        val loginCode = prefs[KEY_LOGIN_CODE].orEmpty()
        val telephone = prefs[KEY_TELEPHONE].orEmpty()
        if (loginCode.isBlank() || telephone.isBlank()) {
            null
        } else {
            QzxySession(
                telephone = telephone,
                loginCode = loginCode,
                userId = prefs[KEY_USER_ID].orEmpty(),
                accountId = prefs[KEY_ACCOUNT_ID].orEmpty(),
                projectId = prefs[KEY_PROJECT_ID].orEmpty(),
                name = prefs[KEY_NAME].orEmpty(),
            )
        }
    }

    suspend fun current(): QzxySession? = sessionFlow.first()

    suspend fun save(session: QzxySession) {
        context.qzxyDataStore.edit { prefs ->
            prefs[KEY_TELEPHONE] = session.telephone
            prefs[KEY_LOGIN_CODE] = session.loginCode
            prefs[KEY_USER_ID] = session.userId
            prefs[KEY_ACCOUNT_ID] = session.accountId
            prefs[KEY_PROJECT_ID] = session.projectId
            prefs[KEY_NAME] = session.name
        }
    }

    suspend fun clear() {
        context.qzxyDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_TELEPHONE = stringPreferencesKey("telephone")
        val KEY_LOGIN_CODE = stringPreferencesKey("login_code")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_ACCOUNT_ID = stringPreferencesKey("account_id")
        val KEY_PROJECT_ID = stringPreferencesKey("project_id")
        val KEY_NAME = stringPreferencesKey("name")
    }
}
