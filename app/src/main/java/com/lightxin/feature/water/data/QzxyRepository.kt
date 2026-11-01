package com.lightxin.feature.water.data

import com.lightxin.core.auth.QzxySession
import com.lightxin.core.auth.QzxySessionStore
import com.lightxin.core.network.ApiConstants
import com.lightxin.feature.water.domain.DeviceStatus
import com.lightxin.feature.water.domain.DeviceUseTime
import com.lightxin.feature.water.domain.RateOrderInfo
import com.lightxin.feature.water.domain.SettleResult
import com.lightxin.feature.water.domain.WaterDevice
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 趣智接口的业务失败。message 可直接展示。 */
open class QzxyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 会话失效（被顶号 / 过期），UI 应回登录页。 */
class QzxySessionExpired(message: String) : QzxyException(message)

@Singleton
class QzxyRepository @Inject constructor(
    private val api: QzxyApi,
    private val sessionStore: QzxySessionStore,
) {

    suspend fun session(): QzxySession? = sessionStore.current()

    suspend fun clearSession() = sessionStore.clear()

    // ─── 登录 ───

    suspend fun loginByPassword(phone: String, password: String): Result<Unit> = guard {
        val resp = api.login(
            telephone = phone,
            password = QzxyCrypto.passwordDigest(password),
            phoneSystem = "android",
            type = "0",
            identifier = "",
            version = ApiConstants.QZXY_VERSION,
        )
        saveSession(requireSuccess(resp, isLogin = true), phone)
    }

    suspend fun requestSmsCode(phone: String): Result<Unit> = guard {
        requireSuccess(
            api.requestSmsCode(
                telephone = phone,
                typeId = "3",
                platform = "1",
                secret = QzxyCrypto.smsSecret(phone),
            ),
        )
        Unit
    }

    suspend fun loginBySmsCode(phone: String, code: String): Result<Unit> = guard {
        val resp = api.loginWithSmsCode(
            telephone = phone,
            smsCode = code,
            type = "5",
            phoneSystem = "android",
            version = ApiConstants.QZXY_VERSION,
        )
        saveSession(requireSuccess(resp, isLogin = true), phone)
    }

    // ─── 余额 / 设备 ───

    suspend fun wallet(): Result<String> = guard {
        val data = requireSuccess(api.wallet()).data ?: throw QzxyException("获取余额失败")
        data.money?.takeIf { it.isNotBlank() } ?: throw QzxyException("获取余额失败")
    }

    suspend fun deviceByMac(mac: String): Result<WaterDevice> = guard {
        val data = requireSuccess(api.deviceByMac(mac, isNew = "1", isHaier = "0")).data
            ?: throw QzxyException("设备不存在")
        WaterDevice(
            deviceId = data.deviceId ?: 0,
            deviceName = data.deviceName.orEmpty(),
            bigTypeId = data.bigTypeId ?: 0,
            smallTypeId = data.smallTypeId ?: 0,
            snCode = data.snCode.orEmpty(),
            macAddress = data.macAddress.orEmpty().ifBlank { mac },
            communicationTypeId = data.communicationTypeId ?: 0,
            isUse = data.isUse ?: 1,
            withholdMoney = data.withholdMoney.orEmpty(),
            showDeviceUseTimeContent = data.showDeviceUseTimeContent,
            deviceUseTimeList = data.deviceUseTimeList?.map {
                DeviceUseTime(startTime = it.startTime, endTime = it.endTime)
            },
            areaName = data.areaName.orEmpty(),
            buildingName = data.buildingName.orEmpty(),
            floorName = data.floorName.orEmpty(),
            roomName = data.roomName.orEmpty(),
        )
    }

    // ─── 订单（抛异常，供 WaterOrderRunner 编排） ───

    suspend fun rateOrder(device: WaterDevice, status: DeviceStatus): RateOrderInfo {
        val session = requireSession()
        val signature = QzxyCrypto.sign(
            session.loginCode,
            mapOf(
                "deviceId" to device.deviceId.toString(),
                "randomNumber" to status.randomNumber,
                "telephone" to session.telephone,
                "xfModel" to "0",
            ),
        )
        val resp = requireSuccess(
            api.rateOrder(
                deviceId = device.deviceId.toString(),
                macAddress = device.macWithColon.replace(":", ""),
                macType = status.macType,
                bigTypeId = device.bigTypeId.toString(),
                smallTypeId = device.smallTypeId.toString(),
                protocolType = status.protocolType,
                randomNumber = status.randomNumber,
                xfModel = "0",
                signature = signature,
            ),
        )
        val data = resp.data ?: throw QzxyException("下单失败")
        return RateOrderInfo(
            downData = data.downData?.takeIf { it.isNotBlank() } ?: throw QzxyException("下单失败"),
            consumeDate = data.consumeDate.orEmpty(),
            rateLi = data.rate ?: 0,
        )
    }

    suspend fun uploadData(status: DeviceStatus, xfDataLower: String): SettleResult {
        val session = requireSession()
        val signature = QzxyCrypto.sign(
            session.loginCode,
            mapOf(
                "loginCode" to session.loginCode,
                "telephone" to session.telephone,
                "xfData" to xfDataLower,
            ),
        )
        val resp = requireSuccess(
            api.uploadData(
                protocolType = status.protocolType,
                randomNumber = status.randomNumber,
                xfData = xfDataLower,
                signature = signature,
            ),
        )
        val data = resp.data ?: throw QzxyException("结算失败")
        return SettleResult(
            consumeMoneyLi = data.consumeMoney ?: 0,
            balanceAfterLi = data.preDeductMoneyAfter ?: 0,
            orderNo = data.orderNo.orEmpty(),
        )
    }

    suspend fun uploadFail(consumeDate: String) {
        val session = requireSession()
        val signature = QzxyCrypto.sign(
            session.loginCode,
            mapOf(
                "consumeDate" to consumeDate,
                "loginCode" to session.loginCode,
                "telephone" to session.telephone,
            ),
        )
        requireSuccess(api.uploadFail(consumeDate, signature))
    }

    // ─── 内部 ───

    private suspend fun requireSession(): QzxySession =
        sessionStore.current() ?: throw QzxySessionExpired("登录已失效，请重新登录")

    private suspend fun saveSession(resp: QzxyLoginResponse, fallbackPhone: String) {
        val data = resp.data ?: throw QzxyException("登录失败")
        val loginCode = data.v3LoginCode?.takeIf { it.isNotBlank() }
            ?: data.loginCode?.takeIf { it.isNotBlank() }
            ?: throw QzxyException("登录失败")
        val account = data.userAccount ?: throw QzxyException("登录失败")
        sessionStore.save(
            QzxySession(
                telephone = data.telephone?.takeIf { it.isNotBlank() } ?: fallbackPhone,
                loginCode = loginCode,
                userId = data.userId.orEmpty(),
                accountId = account.accountId.orEmpty(),
                projectId = account.projectId.orEmpty(),
                name = account.name.orEmpty(),
            ),
        )
    }

    private suspend fun <T : QzxyEnvelope> requireSuccess(env: T, isLogin: Boolean = false): T {
        if (env.success == true && env.errorCode == 0) return env
        val message = errorMessage(env.errorCode, env.errorMessage)
        if (!isLogin && isSessionExpired(env.errorMessage)) {
            sessionStore.clear()
            throw QzxySessionExpired(message)
        }
        throw QzxyException(message)
    }

    private fun isSessionExpired(message: String?): Boolean {
        val text = message ?: return false
        return SESSION_EXPIRED_HINTS.any { text.contains(it) }
    }

    private fun errorMessage(code: Int?, fallback: String?): String = when (code) {
        12 -> "手机号或密码错误"
        46 -> "验证码错误"
        29 -> "验证码已失效"
        53 -> "请勿频繁获取验证码"
        307 -> "设备使用中"
        4004 -> "设备不存在"
        170 -> "签名错误"
        226 -> "加密校验失败"
        else -> fallback?.takeIf { it.isNotBlank() } ?: "请求失败"
    }

    private suspend fun <T> guard(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: QzxyException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(QzxyException("网络异常，请检查连接", e))
    } catch (e: Exception) {
        Result.failure(QzxyException("请求失败", e))
    }

    private companion object {
        val SESSION_EXPIRED_HINTS = listOf("登录", "失效", "过期", "请重新")
    }
}
