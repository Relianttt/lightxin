package com.lightxin.feature.running.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `same point distance is zero`() {
        val meters = GeoDistance.metersBetween(
            lat1 = 31.0,
            lon1 = 118.0,
            lat2 = 31.0,
            lon2 = 118.0,
        )

        assertEquals(0.0, meters, 0.0001)
    }

    @Test
    fun `one longitude degree at equator is about one hundred eleven kilometers`() {
        val meters = GeoDistance.metersBetween(
            lat1 = 0.0,
            lon1 = 0.0,
            lat2 = 0.0,
            lon2 = 1.0,
        )

        assertEquals(111_195.0, meters, 5.0)
    }
}
