package com.lightxin.feature.water.data

/**
 * 趣智统一响应外壳。**不要**用泛型 `BaseResponse<T>`：R8 会擦除 suspend 泛型签名
 * （见 `.agents/notes/2026-10-25-learning-gson-typetoken-r8.md`），每个接口写具体类。
 */
interface QzxyEnvelope {
    val success: Boolean?
    val errorCode: Int?
    val errorMessage: String?
}

data class QzxyLoginResponse(
    override val success: Boolean? = null,
    override val errorCode: Int? = null,
    override val errorMessage: String? = null,
    val data: QzxyLoginData? = null,
) : QzxyEnvelope

data class QzxyLoginData(
    val loginCode: String? = null,
    val v3LoginCode: String? = null,
    val userId: String? = null,
    val telephone: String? = null,
    val userAccount: QzxyUserAccount? = null,
)

data class QzxyUserAccount(
    val projectId: String? = null,
    val accountId: String? = null,
    val name: String? = null,
)

/** 没有业务 data 的接口（获取验证码、上传失败）。 */
data class QzxySimpleResponse(
    override val success: Boolean? = null,
    override val errorCode: Int? = null,
    override val errorMessage: String? = null,
) : QzxyEnvelope

data class QzxyWalletResponse(
    override val success: Boolean? = null,
    override val errorCode: Int? = null,
    override val errorMessage: String? = null,
    val data: QzxyWalletData? = null,
) : QzxyEnvelope

data class QzxyWalletData(
    val money: String? = null,
)

data class QzxyDeviceResponse(
    override val success: Boolean? = null,
    override val errorCode: Int? = null,
    override val errorMessage: String? = null,
    val data: QzxyDeviceData? = null,
) : QzxyEnvelope

data class QzxyDeviceData(
    val deviceId: Long? = null,
    val deviceName: String? = null,
    val bigTypeId: Int? = null,
    val smallTypeId: Int? = null,
    val snCode: String? = null,
    val macAddress: String? = null,
    val communicationTypeId: Int? = null,
    /** 仅诊断。类型未知（布尔或 0/1 都可能），用 Any 防止 Gson 解析失败拖垮整个响应。 */
    val isBle: Any? = null,
    /** 仅诊断。 */
    val realMac: String? = null,
    val isUse: Int? = null,
    /** 预扣金额，元。服务端下发数字（1.0），Gson 会转成字符串。 */
    val withholdMoney: String? = null,
    val showDeviceUseTimeContent: String? = null,
    val deviceUseTimeList: List<QzxyDeviceUseTime>? = null,
    val areaName: String? = null,
    val buildingName: String? = null,
    val floorName: String? = null,
    val roomName: String? = null,
)

data class QzxyDeviceUseTime(
    val startTime: String? = null,
    val endTime: String? = null,
)

data class QzxyRateOrderResponse(
    override val success: Boolean? = null,
    override val errorCode: Int? = null,
    override val errorMessage: String? = null,
    val data: QzxyRateOrderData? = null,
) : QzxyEnvelope

data class QzxyRateOrderData(
    /** 96 位大写 hex，直接转小写写进 0x21。 */
    val downData: String? = null,
    /** 厘。 */
    val rate: Long? = null,
    val preDeductMoney: String? = null,
    /** yyyyMMddHHmmss，上传失败时要用。 */
    val consumeDate: String? = null,
)

data class QzxyUploadDataResponse(
    override val success: Boolean? = null,
    override val errorCode: Int? = null,
    override val errorMessage: String? = null,
    val data: QzxyUploadDataData? = null,
) : QzxyEnvelope

data class QzxyUploadDataData(
    /** 厘。 */
    val consumeMoney: Long? = null,
    /** 厘。 */
    val preDeductMoneyAfter: Long? = null,
    val orderNo: String? = null,
    val consumeTime: String? = null,
)
