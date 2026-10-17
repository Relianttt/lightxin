package com.lightxin.feature.running.domain

import kotlin.math.cos

object RunningCampus {

    // 校园中心：2026-10-14 由用户提供，替换掉来源不明的 31.13893 / 118.628131（两点相距约 620 m）。
    //
    // 坐标系已确认为 **WGS-84**（用户说明来源是 GPX / 谷歌地球）。因此内置环线顶点与手机原始定位同源，
    // 上传前一律走 CoordinateConverter.wgs84ToBd09；围栏判定也继续拿转换前的原始点来比。
    //
    // 服务端显示侧要 BD-09 这件事现在有两条独立证据：官方 H5 用 BMap + Convertor.translate(1→5)，
    // 以及 2026-10-14 的一次实测——拿一份**已经转成 GCJ-02** 的轨迹喂进定位、再按 WGS-84 转 BD-09 上传，
    // 结果正好朝东南偏一步 WGS→GCJ（本地算得 531 m，实测约 530 m）。多出来的那一步就是偏移量本身。
    const val CENTER_LATITUDE = 31.134154
    const val CENTER_LONGITUDE = 118.631502

    private const val METERS_PER_LAT_DEGREE = 111_320.0
    private const val CAMPUS_HALF_HEIGHT_METERS = 600.0
    private const val CAMPUS_HALF_WIDTH_METERS = 600.0

    val minLatitude: Double = CENTER_LATITUDE - metersToLatitude(CAMPUS_HALF_HEIGHT_METERS)
    val maxLatitude: Double = CENTER_LATITUDE + metersToLatitude(CAMPUS_HALF_HEIGHT_METERS)
    val minLongitude: Double = CENTER_LONGITUDE - metersToLongitude(CAMPUS_HALF_WIDTH_METERS)
    val maxLongitude: Double = CENTER_LONGITUDE + metersToLongitude(CAMPUS_HALF_WIDTH_METERS)

    fun pointAt(latOffsetMeters: Double, lngOffsetMeters: Double): TrackPoint {
        return TrackPoint(
            latitude = CENTER_LATITUDE + metersToLatitude(latOffsetMeters),
            longitude = CENTER_LONGITUDE + metersToLongitude(lngOffsetMeters),
        )
    }

    private fun metersToLatitude(meters: Double): Double = meters / METERS_PER_LAT_DEGREE

    private fun metersToLongitude(meters: Double): Double {
        return meters / (METERS_PER_LAT_DEGREE * cos(Math.toRadians(CENTER_LATITUDE)))
    }
}
