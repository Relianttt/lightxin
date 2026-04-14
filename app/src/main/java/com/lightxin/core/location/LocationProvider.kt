package com.lightxin.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 获取最后已知位置（快速，可能为null） */
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    /** 持续获取GPS位置更新，返回 Flow<Location> */
    fun locationUpdates(intervalMs: Long = 3000L, minDistanceM: Float = 2f): Flow<Location> {
        return callbackFlow {
            if (!hasLocationPermission()) {
                close(SecurityException("缺少定位权限"))
                return@callbackFlow
            }

            val listener = LocationListener { location -> trySend(location) }

            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMs,
                    minDistanceM,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (e: SecurityException) {
                close(e)
                return@callbackFlow
            }

            awaitClose {
                locationManager.removeUpdates(listener)
            }
        }
    }
}
