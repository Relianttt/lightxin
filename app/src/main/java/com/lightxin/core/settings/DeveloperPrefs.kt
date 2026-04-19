package com.lightxin.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lightxin_dev")

@Singleton
class DeveloperPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_ADVANCED_ENABLED = booleanPreferencesKey("advanced_enabled")
    }

    val isAdvancedEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ADVANCED_ENABLED] == true
    }

    suspend fun setAdvancedEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADVANCED_ENABLED] = enabled
        }
    }
}
