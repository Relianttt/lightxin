package com.lightxin.feature.water

import com.lightxin.feature.water.domain.parseDeviceCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCodeTest {

    @Test
    fun `二维码取最后一个逗号后的内容`() {
        assertEquals("AA:BB:CC:DD:EE:FF", parseDeviceCode("KLCXKJ-Water,M,AABBCCDDEEFF"))
    }

    @Test
    fun `手动输入接受三种写法`() {
        assertEquals("AA:BB:CC:DD:EE:FF", parseDeviceCode("AABBCCDDEEFF"))
        assertEquals("AA:BB:CC:DD:EE:FF", parseDeviceCode("aabbccddeeff"))
        assertEquals("AA:BB:CC:DD:EE:FF", parseDeviceCode("AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", parseDeviceCode(" aa bb cc dd ee ff "))
    }

    @Test
    fun `长度或字符不对返回 null`() {
        assertNull(parseDeviceCode(""))
        assertNull(parseDeviceCode("AABBCCDDEE"))
        assertNull(parseDeviceCode("AABBCCDDEEFFF"))
        assertNull(parseDeviceCode("GGHHIIJJKKLL"))
    }
}
