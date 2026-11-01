package com.lightxin.feature.water

import com.lightxin.feature.water.domain.DeviceUseTime
import com.lightxin.feature.water.domain.WaterDevice
import com.lightxin.feature.water.domain.deviceSubtitle
import com.lightxin.feature.water.domain.deviceTitle
import com.lightxin.feature.water.domain.formatLi
import com.lightxin.feature.water.domain.formatYuan
import com.lightxin.feature.water.domain.useTimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterFormatTest {

    @Test
    fun `厘转元`() {
        assertEquals("¥0.05", formatLi(50))
        assertEquals("¥0.011", formatLi(11))
        assertEquals("¥1.00", formatLi(1000))
        assertEquals("¥0.11", formatLi(110))
    }

    @Test
    fun `元字符串按厘重排`() {
        assertEquals("¥14.11", formatYuan("14.110"))
        assertEquals("¥14.115", formatYuan("14.115"))
        assertEquals("¥1.00", formatYuan("1.0"))
        assertEquals("—", formatYuan("abc"))
        assertEquals("—", formatYuan(""))
    }

    @Test
    fun `设备标题与副标题`() {
        val hotWater = WaterDevice(
            bigTypeId = WaterDevice.TYPE_HOT_WATER,
            roomName = "214",
            areaName = "A36公寓",
            buildingName = "中楼",
            floorName = "2层",
        )
        assertEquals("热水 · 214", deviceTitle(hotWater))
        assertEquals("A36公寓 · 中楼 · 2层", deviceSubtitle(hotWater))

        val drinking = WaterDevice(
            bigTypeId = WaterDevice.TYPE_DRINKING,
            roomName = "开水机1-1",
            areaName = "A36公寓",
            buildingName = "中楼",
            floorName = "1层",
        )
        assertEquals("饮水 · 开水机1-1", deviceTitle(drinking))
    }

    @Test
    fun `可用时段优先服务端文案`() {
        val fromContent = WaterDevice(showDeviceUseTimeContent = "06:30-23:59")
        assertEquals("06:30-23:59", useTimeText(fromContent))
    }

    @Test
    fun `可用时段按列表拼接并忽略空项`() {
        val device = WaterDevice(
            deviceUseTimeList = listOf(
                DeviceUseTime("06:30", "08:00"),
                DeviceUseTime("", ""),
                DeviceUseTime("11:30", "23:59"),
            ),
        )
        assertEquals("06:30-08:00，11:30-23:59", useTimeText(device))
    }

    @Test
    fun `饮水机的空时段列表返回 null`() {
        val device = WaterDevice(deviceUseTimeList = listOf(DeviceUseTime("", "")))
        assertNull(useTimeText(device))
    }
}
