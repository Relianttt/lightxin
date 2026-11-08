package com.lightxin.feature.water.data.bt

import com.lightxin.feature.water.data.QzxyException
import com.lightxin.feature.water.domain.DeviceStatus
import com.lightxin.feature.water.domain.UnsupportedProtocol
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull

/** 一帧设备数据。[direction] 请求 0x80、应答 0x81。 */
data class Frame(val direction: Int, val cmd: Int, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return direction == other.direction && cmd == other.cmd && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = (direction * 31 + cmd) * 31 + data.contentHashCode()
}

/**
 * 厂商水控板的帧协议（报告 §9.4–9.7）。应用层与 BLE 版本一致，只换了传输层。
 *
 * 线上文本是 `'#' + HEX + '\n'`；手机端发小写，设备回大写，解析两者都接受。
 */
object QzxyFrame {

    const val CMD_STATUS = 0x23
    const val CMD_RATE = 0x21
    const val CMD_CLOSE = 0x22
    const val CMD_COLLECT = 0x85
    const val CMD_ACK = 0x86

    const val REQUEST = 0x80
    const val RESPONSE = 0x81

    private const val HEAD_0 = 0x60
    private const val HEAD_1 = 0x00
    private const val TAIL = 0x16

    /** 应答结束字节（0x80 = 成功）。 */
    const val OK = 0x80

    /** 生成待发送的 ASCII 字节，含 `#` 前缀与小写 hex、`\n` 结尾。 */
    fun encode(cmd: Int, data: ByteArray): ByteArray {
        val len = data.size + 3
        val frame = ByteArray(len + 5)
        frame[0] = HEAD_0.toByte()
        frame[1] = HEAD_1.toByte()
        frame[2] = len.toByte()
        frame[3] = REQUEST.toByte()
        frame[4] = cmd.toByte()
        frame[5] = 0
        data.copyInto(frame, 6)
        frame[frame.size - 2] = crc(REQUEST, cmd, data).toByte()
        frame[frame.size - 1] = TAIL.toByte()
        return ("#" + frame.toHexLower() + "\n").toByteArray(Charsets.US_ASCII)
    }

    /** 解析一行设备文本；校验失败返回 null。 */
    fun decodeLine(line: String): Frame? {
        val text = line.trim().removePrefix("#").trim()
        if (text.isEmpty() || text.length % 2 != 0) return null
        val bytes = hexToBytesOrNull(text) ?: return null
        if (bytes.size < 8) return null
        if (bytes[0].toInt() and 0xFF != HEAD_0) return null
        if (bytes[1].toInt() and 0xFF != HEAD_1) return null
        val len = bytes[2].toInt() and 0xFF
        if (len < 3 || len + 5 != bytes.size) return null
        val direction = bytes[3].toInt() and 0xFF
        if (direction != REQUEST && direction != RESPONSE) return null
        val cmd = bytes[4].toInt() and 0xFF
        if (bytes[5].toInt() != 0) return null
        if (bytes[bytes.size - 1].toInt() and 0xFF != TAIL) return null
        val data = bytes.copyOfRange(6, bytes.size - 2)
        if (data.size != len - 3) return null
        if (bytes[bytes.size - 2].toInt() and 0xFF != crc(direction, cmd, data)) return null
        return Frame(direction, cmd, data)
    }

    /**
     * 解析 `0x23` 应答载荷（报告 §9.5）。
     * 支持 ≥31 字节长布局（本校实测）与 28 字节短布局，其余长度抛 [UnsupportedProtocol]。
     *
     * 短布局取自 linyu `DeviceQueryParser`（梧州学院真机验证，本校未验证）：无 protocolType（传空串）、
     * randomNumber 只有 2 字节原样透传、state=`p[19]`、devType/a1=`p[23]`/`p[24]`。
     * acct 沿用 `p[11..12]` 是推断：短帧前 19 字节与长帧结构一致。
     */
    fun parseStatus(payload: ByteArray): DeviceStatus {
        val acct = if (payload.size >= 13) {
            ((payload[11].toInt() and 0xFF) shl 8) or (payload[12].toInt() and 0xFF)
        } else {
            0
        }
        if (payload.size == 28) {
            return DeviceStatus(
                protocolType = "",
                randomNumber = payload.copyOfRange(21, 23).toHexLower(),
                state = payload[19].toInt() and 0xFF,
                macType = hex2(payload[23].toInt() and 0xFF) + hex2(payload[24].toInt() and 0xFF),
                acct = acct,
            )
        }
        if (payload.size < 31) throw UnsupportedProtocol("设备状态长度不支持：${payload.size}")
        val devType = payload[29].toInt() and 0xFF
        val a1 = payload[30].toInt() and 0xFF
        return DeviceStatus(
            protocolType = hex2(payload[19].toInt() and 0xFF),
            randomNumber = payload.copyOfRange(24, 28).toHexLower(),
            state = payload[28].toInt() and 0xFF,
            macType = hex2(devType) + hex2(a1),
            acct = acct,
        )
    }

    /**
     * `0x86` 写回载荷：`xfData[1..18]` + `xfData[19..22]` 字节反转（小端 u32 → 大端），共 22 字节。
     */
    fun ackPayload(xfData: ByteArray): ByteArray {
        require(xfData.size >= 23) { "xfData 至少 23 字节，实际 ${xfData.size}" }
        val head = xfData.copyOfRange(1, 19)
        val tail = xfData.copyOfRange(19, 23).reversedArray()
        return head + tail
    }

    private fun crc(direction: Int, cmd: Int, data: ByteArray): Int {
        var sum = direction + cmd
        for (b in data) sum += b.toInt() and 0xFF
        return sum and 0xFF
    }

    private fun hex2(value: Int): String = HEX[value shr 4 and 0x0F].toString() + HEX[value and 0x0F]
}

/**
 * 把分批到达的字节拼成 `\n` 结尾的文本行并解码成帧。
 * SPP 是字节流，BLE 是每包 ≤20 字节的通知，两者共用。
 */
class FrameAssembler {
    private val line = StringBuilder()

    fun feed(bytes: ByteArray, count: Int = bytes.size): List<Frame> {
        var frames: MutableList<Frame>? = null
        for (i in 0 until count) {
            when (val char = (bytes[i].toInt() and 0xFF).toChar()) {
                '\n' -> {
                    QzxyFrame.decodeLine(line.toString())?.let {
                        (frames ?: mutableListOf<Frame>().also { list -> frames = list }).add(it)
                    }
                    line.setLength(0)
                }

                '\r' -> Unit

                else -> {
                    line.append(char)
                    if (line.length > MAX_LINE) line.setLength(0)
                }
            }
        }
        return frames.orEmpty()
    }

    private companion object {
        const val MAX_LINE = 4096
    }
}

/** 等待指定命令的应答帧，丢弃无关帧；超时或连接断开抛 [QzxyException]。 */
internal suspend fun Channel<Frame>.awaitResponse(cmd: Int, timeoutMs: Long): Frame {
    val matched = try {
        withTimeoutOrNull(timeoutMs) {
            var found: Frame? = null
            while (found == null) {
                val candidate = receive()
                if (candidate.direction == QzxyFrame.RESPONSE && candidate.cmd == cmd) found = candidate
            }
            found
        }
    } catch (e: ClosedReceiveChannelException) {
        throw QzxyException("与设备断开连接", e)
    }
    return matched ?: throw QzxyException("等待设备响应超时")
}

private val HEX = "0123456789abcdef".toCharArray()

/** 小写 hex，用于帧文本与 HTTP 的 `xfData`。 */
fun ByteArray.toHexLower(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX[v shr 4 and 0x0F]).append(HEX[v and 0x0F])
    }
    return out.toString()
}

fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "hex 长度必须为偶数" }
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = Character.digit(hex[i * 2], 16)
        val lo = Character.digit(hex[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "非法 hex 字符" }
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

private fun hexToBytesOrNull(hex: String): ByteArray? = try {
    hexToBytes(hex)
} catch (e: IllegalArgumentException) {
    null
}
