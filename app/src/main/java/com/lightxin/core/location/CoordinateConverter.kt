package com.lightxin.core.location

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * WGS-84 → GCJ-02 → BD-09 坐标转换。
 * 服务端使用百度BD-09坐标系，Android原生定位返回WGS-84。
 */
object CoordinateConverter {

    private const val PI = Math.PI
    private const val X_PI = PI * 3000.0 / 180.0
    private const val A = 6378245.0 // 长半轴
    private const val EE = 0.00669342162296594323 // 偏心率平方

    data class LatLng(val latitude: Double, val longitude: Double)

    /** WGS-84 → BD-09（完整链路） */
    fun wgs84ToBd09(lat: Double, lng: Double): LatLng {
        val gcj = wgs84ToGcj02(lat, lng)
        return gcj02ToBd09(gcj.latitude, gcj.longitude)
    }

    /** WGS-84 → GCJ-02 */
    fun wgs84ToGcj02(lat: Double, lng: Double): LatLng {
        if (outOfChina(lat, lng)) return LatLng(lat, lng)
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return LatLng(lat + dLat, lng + dLng)
    }

    /** GCJ-02 → BD-09 */
    fun gcj02ToBd09(lat: Double, lng: Double): LatLng {
        val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = Math.atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
        val bdLng = z * cos(theta) + 0.0065
        val bdLat = z * sin(theta) + 0.006
        return LatLng(bdLat, bdLng)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }
}
