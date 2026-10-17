package com.lightxin.core.location

import com.lightxin.feature.running.domain.GeoDistance
import com.lightxin.feature.running.domain.RunningCampus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转换公式里 `0.000003` 被写成 `0.00003` 这类一位数的错，肉眼看不出来，却会让轨迹平移几百米、
 * 在官方地图上落到完全不相干的位置。这里用黄金值和偏移量级带把它钉住。
 */
class CoordinateConverterTest {

    @Test
    fun `wgs84 to bd09 matches the golden value at the campus`() {
        val bd = CoordinateConverter.wgs84ToBd09(31.138771, 118.615759)

        assertEquals(31.1421305, bd.latitude, 1e-6)
        assertEquals(118.6272907, bd.longitude, 1e-6)
    }

    @Test
    fun `each step of the chain stays in its documented offset band`() {
        val lat = RunningCampus.CENTER_LATITUDE
        val lng = RunningCampus.CENTER_LONGITUDE

        val gcj = CoordinateConverter.wgs84ToGcj02(lat, lng)
        val bd = CoordinateConverter.gcj02ToBd09(gcj.latitude, gcj.longitude)

        val wgsToGcj = GeoDistance.metersBetween(lat, lng, gcj.latitude, gcj.longitude)
        val gcjToBd = GeoDistance.metersBetween(gcj.latitude, gcj.longitude, bd.latitude, bd.longitude)
        val total = GeoDistance.metersBetween(lat, lng, bd.latitude, bd.longitude)

        assertTrue("WGS→GCJ ${wgsToGcj.toInt()} m", wgsToGcj in 300.0..700.0)
        assertTrue("GCJ→BD ${gcjToBd.toInt()} m", gcjToBd in 400.0..1200.0)
        assertTrue("WGS→BD ${total.toInt()} m", total in 800.0..1400.0)
    }

    @Test
    fun `coordinates outside china skip the gcj offset entirely`() {
        val gcj = CoordinateConverter.wgs84ToGcj02(48.8584, 2.2945)

        assertEquals(48.8584, gcj.latitude, 1e-9)
        assertEquals(2.2945, gcj.longitude, 1e-9)
    }
}
