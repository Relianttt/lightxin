package com.lightxin.feature.water.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lightxin.feature.water.domain.ActiveOrder
import com.lightxin.feature.water.domain.RecentDevice
import com.lightxin.feature.water.domain.WaterOrderStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.waterDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "qzxy_water")

/** 最近使用的设备（最多 5 个，最近在前）与未结算订单。 */
@Singleton
class WaterLocalStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : WaterOrderStore {

    private val gson = Gson()
    private val recentType = TypeToken.getParameterized(List::class.java, RecentDevice::class.java).type

    val activeOrder: Flow<ActiveOrder?> = context.waterDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_ORDER]?.let { runCatching { gson.fromJson(it, ActiveOrder::class.java) }.getOrNull() }
    }

    val recentDevices: Flow<List<RecentDevice>> = context.waterDataStore.data.map { prefs ->
        val raw = prefs[KEY_RECENT_DEVICES] ?: return@map emptyList()
        runCatching { gson.fromJson<List<RecentDevice>>(raw, recentType) }.getOrNull().orEmpty()
    }

    override suspend fun setActiveOrder(order: ActiveOrder) {
        context.waterDataStore.edit { it[KEY_ACTIVE_ORDER] = gson.toJson(order) }
    }

    override suspend fun clearActiveOrder() {
        context.waterDataStore.edit { it.remove(KEY_ACTIVE_ORDER) }
    }

    override suspend fun addRecentDevice(device: RecentDevice) {
        val current = recentDevices.first()
        val next = (listOf(device) + current.filterNot { it.mac == device.mac }).take(MAX_RECENT)
        context.waterDataStore.edit { it[KEY_RECENT_DEVICES] = gson.toJson(next, recentType) }
    }

    private companion object {
        const val MAX_RECENT = 5
        val KEY_ACTIVE_ORDER = stringPreferencesKey("active_order")
        val KEY_RECENT_DEVICES = stringPreferencesKey("recent_devices")
    }
}
