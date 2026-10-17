package com.lightxin.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rememberedLoginDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "lightxin_login")

/**
 * 「记住密码」的落盘处，只服务登录页预填。
 *
 * 密码不进 DataStore 明文，先用 AndroidKeyStore 里的 AES/GCM 密钥加密再存 —— 密钥出不了
 * 这台设备，导出 DataStore 文件也解不开。
 *
 * 它仍然只保护「别人捡到解锁状态的手机」这一层：本机任何能跑通屏解锁的进程都能解密。
 * 别在它上面再叠免密登录之类的东西，也不要把 token 塞进来。
 */
@Singleton
class RememberedLoginStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun loadUserCode(): String = withContext(Dispatchers.IO) {
        context.rememberedLoginDataStore.data.first()[KEY_USER_CODE].orEmpty()
    }

    /** 只有勾选过「记住密码」且能解密时才返回密码，否则 null */
    suspend fun loadPassword(): String? = withContext(Dispatchers.IO) {
        val prefs = context.rememberedLoginDataStore.data.first()
        if (prefs[KEY_REMEMBER] != true) return@withContext null
        val payload = prefs[KEY_ENCRYPTED_PASSWORD] ?: return@withContext null
        val password = runCatching { decrypt(payload) }
            .onFailure { Log.w(LOG_TAG, "已存密码解密失败，按未记住处理", it) }
            .getOrNull()
        if (password == null) {
            // 密钥被系统回收（换机 / 恢复备份）时清掉密文，别每次启动都白试一遍
            context.rememberedLoginDataStore.edit {
                it.remove(KEY_ENCRYPTED_PASSWORD)
                it[KEY_REMEMBER] = false
            }
        }
        password
    }

    /** 登录成功后调用；[remember] 为 false 时清掉已存的密文，只保留学号。 */
    suspend fun save(userCode: String, password: String, remember: Boolean) = withContext(Dispatchers.IO) {
        val encrypted = if (remember && password.isNotBlank()) {
            runCatching { encrypt(password) }
                .onFailure { Log.w(LOG_TAG, "密码加密失败，本次不记住", it) }
                .getOrNull()
        } else {
            null
        }
        context.rememberedLoginDataStore.edit { prefs ->
            prefs[KEY_USER_CODE] = userCode
            prefs[KEY_REMEMBER] = encrypted != null
            if (encrypted != null) {
                prefs[KEY_ENCRYPTED_PASSWORD] = encrypted
            } else {
                prefs.remove(KEY_ENCRYPTED_PASSWORD)
            }
        }
    }

    private fun encrypt(password: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.ENCRYPT_MODE, obtainKey())
        }
        val body = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        // iv 拼在密文前面，解密时按固定长度切开
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        require(raw.size > IV_LENGTH) { "密文长度异常" }
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(
                Cipher.DECRYPT_MODE,
                obtainKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, raw, 0, IV_LENGTH),
            )
        }
        return String(cipher.doFinal(raw, IV_LENGTH, raw.size - IV_LENGTH), Charsets.UTF_8)
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        val KEY_USER_CODE = stringPreferencesKey("saved_user_code")
        val KEY_REMEMBER = booleanPreferencesKey("remember_password")
        val KEY_ENCRYPTED_PASSWORD = stringPreferencesKey("encrypted_password")

        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lightxin.remembered_login"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val LOG_TAG = "RememberedLoginStore"
    }
}
