package com.lightxin.feature.water

import com.lightxin.feature.water.data.bt.FrameAssembler
import com.lightxin.feature.water.data.bt.QzxyFrame
import com.lightxin.feature.water.data.bt.hexToBytes
import com.lightxin.feature.water.data.bt.toHexLower
import com.lightxin.feature.water.domain.UnsupportedProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QzxyFrameTest {

    @Test
    fun `编码查状态帧`() {
        assertEquals(
            "#60000480230000a316\n",
            QzxyFrame.encode(QzxyFrame.CMD_STATUS, byteArrayOf(0x00)).toString(Charsets.US_ASCII),
        )
    }

    @Test
    fun `解析大小写与回车`() {
        val upper = "#60000480230000A316\r"
        val lower = "#60000480230000a316"
        val fromUpper = QzxyFrame.decodeLine(upper)!!
        val fromLower = QzxyFrame.decodeLine(lower)!!
        assertEquals(QzxyFrame.REQUEST, fromUpper.direction)
        assertEquals(QzxyFrame.CMD_STATUS, fromUpper.cmd)
        assertTrue(fromUpper.data.contentEquals(byteArrayOf(0x00)))
        assertEquals(fromUpper, fromLower)
    }

    @Test
    fun `解析 0x23 应答`() {
        // 48 字节状态载荷，布局见报告 §9.5；数值为构造值。
        val payload = ByteArray(48)
        payload[0] = 0x80.toByte()
        payload[7] = 0x09
        payload[8] = 0x3a
        payload[11] = 0x2d
        payload[12] = 0xe5.toByte()
        payload[19] = 0x10
        payload[24] = 0x69
        payload[25] = 0x02
        payload[26] = 0x00
        payload[27] = 0x00
        payload[28] = 0x00
        payload[29] = 0x00
        payload[30] = 0x39
        val len = payload.size + 3
        var sum = 0x81 + QzxyFrame.CMD_STATUS
        for (b in payload) sum += b.toInt() and 0xFF
        val raw = byteArrayOf(0x60, 0x00, len.toByte(), 0x81.toByte(), QzxyFrame.CMD_STATUS.toByte(), 0x00) +
            payload + byteArrayOf((sum and 0xFF).toByte(), 0x16)
        val frame = QzxyFrame.decodeLine("#" + raw.toHexLower())!!
        val status = QzxyFrame.parseStatus(frame.data)
        assertEquals("10", status.protocolType)
        assertEquals("69020000", status.randomNumber)
        assertEquals("0039", status.macType)
        assertEquals(0, status.state)
        assertEquals(11749, status.acct)
        assertTrue(status.isIdle)
    }

    @Test
    fun `crc 错、尾字节错、长度错都返回 null`() {
        assertNull(QzxyFrame.decodeLine("#60000480230000a317"))
        assertNull(QzxyFrame.decodeLine("#60000480230000a315"))
        assertNull(QzxyFrame.decodeLine("#60000580230000a316"))
        assertNull(QzxyFrame.decodeLine("#600004802300a316"))
        assertNull(QzxyFrame.decodeLine("6000048023"))
        assertNull(QzxyFrame.decodeLine("#zz000480230000a316"))
    }

    @Test
    fun `短载荷状态抛 UnsupportedProtocol`() {
        try {
            QzxyFrame.parseStatus(ByteArray(28))
            fail("应当抛 UnsupportedProtocol")
        } catch (e: UnsupportedProtocol) {
            assertEquals(true, e.message!!.contains("28"))
        }
    }

    @Test
    fun `写回载荷取 1 到 18 字节并把末尾 u32 反转`() {
        // 42 字节布局照报告 §9.6，设备号/账号/SN 换成合成值（抓包真实值不写入代码）
        val xf = hexToBytes(
            "80261101233444000001fa000003930000111102000000" +
                "02000003e80000000b00000032aabbccddeeff",
        )
        assertEquals(42, xf.size)
        assertEquals(
            "261101233444000001fa000003930000111100000002",
            QzxyFrame.ackPayload(xf).toHexLower(),
        )
    }

    @Test
    fun `分包到达的字节拼成整帧`() {
        val assembler = FrameAssembler()
        val text = "#600004812100802216\n#60000480230000a316\n".toByteArray(Charsets.US_ASCII)
        val first = assembler.feed(text.copyOfRange(0, 7))
        val rest = assembler.feed(text.copyOfRange(7, text.size))

        assertTrue(first.isEmpty())
        assertEquals(listOf(QzxyFrame.CMD_RATE, QzxyFrame.CMD_STATUS), rest.map { it.cmd })
        assertEquals(QzxyFrame.RESPONSE, rest[0].direction)
    }

    @Test
    fun `热水表 BLE 实测：0x85 应答经 20 字节分包后算出的写回帧与官方一致`() {
        // 11-02 23:57 btsnoop：FF01 通知按 20 字节分包，手机随后向 FF02 写 0x86
        val notified = "#60002D81850080261102234710000001FA0000039300002DE50200000002" +
            "000013880000051400000032C47F0E269CB18A16\n"
        val assembler = FrameAssembler()
        val frames = notified.toByteArray(Charsets.US_ASCII).toList().chunked(20)
            .flatMap { assembler.feed(it.toByteArray()) }
        val collected = frames.single().data

        val ack = QzxyFrame.encode(QzxyFrame.CMD_ACK, QzxyFrame.ackPayload(collected))
        assertEquals(
            "#6000198086002611022" + "34710000001fa0000039" + "300002de5000000025e1" + "6\n",
            ack.toString(Charsets.US_ASCII),
        )
    }
}
