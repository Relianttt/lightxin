package com.lightxin.feature.water.domain

/** 设备可用时段一项。 */
data class DeviceUseTime(
    val startTime: String? = null,
    val endTime: String? = null,
)

/**
 * `GET /device/info/mac` → `WaterDevice` 转换后的业务设备，只保留 UI 与订单流程需要的字段。
 */
data class WaterDevice(
    val deviceId: Long = 0,
    val deviceName: String = "",
    val bigTypeId: Int = 0,
    val smallTypeId: Int = 0,
    val snCode: String = "",
    val macAddress: String = "",
    val communicationTypeId: Int = 0,
    val isUse: Int = 1,
    /** 预扣金额，元，服务端原串（如 "1.0"）。 */
    val withholdMoney: String = "",
    /** 若服务端直接给了可用时段文案，优先用它。 */
    val showDeviceUseTimeContent: String? = null,
    val deviceUseTimeList: List<DeviceUseTime>? = null,
    val areaName: String = "",
    val buildingName: String = "",
    val floorName: String = "",
    val roomName: String = "",
) {
    val isDrinking: Boolean get() = bigTypeId == TYPE_DRINKING
    val isHotWater: Boolean get() = bigTypeId == TYPE_HOT_WATER
    val isSupported: Boolean
        get() = (isDrinking || isHotWater) && communicationTypeId == 0
    val isEnabled: Boolean get() = isUse == 1

    /** 带冒号大写的 MAC，蓝牙与 HTTP 都用这个形式。 */
    val macWithColon: String get() = macAddress.uppercase()

    companion object {
        const val TYPE_HOT_WATER = 4
        const val TYPE_DRINKING = 5
    }
}

/** `0x23` 状态应答解析结果（报告 §9.5）。 */
data class DeviceStatus(
    /** 2 位小写 hex，如 "10"。 */
    val protocolType: String,
    /** 8 位小写 hex，如 "69020000"。 */
    val randomNumber: String,
    /** 0 空闲 / 1 使用中 / 3、6 待结算。 */
    val state: Int,
    /** 2 位小写 hex，如 "0039"。 */
    val macType: String,
    /** 设备当前记账账号（u16）。 */
    val acct: Int,
) {
    val isIdle: Boolean get() = state == 0
    val isInUse: Boolean get() = state == 1
    val isPendingSettle: Boolean get() = state == 3 || state == 6
}

/**
 * 已开阀但尚未完成结算的订单。落盘，App 被杀后仍可恢复结算。
 */
data class ActiveOrder(
    val mac: String = "",
    val deviceId: Long = 0,
    val deviceName: String = "",
    val bigTypeId: Int = 0,
    /** rateOrder 返回的 consumeDate（yyyyMMddHHmmss），上传失败时要用。 */
    val consumeDate: String = "",
    val startedAt: Long = 0,
)

/** `rateOrder` 的返回：开阀要 [downData]，撤销要 [consumeDate]。 */
data class RateOrderInfo(
    /** 96 位 hex，原样转小写写进 0x21。 */
    val downData: String,
    /** yyyyMMddHHmmss。 */
    val consumeDate: String,
    /** 厘。 */
    val rateLi: Long,
)

/** 结算结果，金额单位均为厘。 */
data class SettleResult(
    val consumeMoneyLi: Long,
    val balanceAfterLi: Long,
    val orderNo: String,
)

/** 最近使用的设备。位置字段只用于列表展示，缺失时列表退化为只显示设备名。 */
data class RecentDevice(
    val mac: String = "",
    val deviceName: String = "",
    val bigTypeId: Int = 0,
    val roomName: String = "",
    val areaName: String = "",
    val buildingName: String = "",
    val floorName: String = "",
)

/** 设备状态帧不是已知布局（v1 只支持 ≥31 字节布局）。 */
class UnsupportedProtocol(message: String) : Exception(message)
