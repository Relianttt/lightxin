package com.lightxin.feature.checkin.data

/**
 * 查寝服务端拒绝了本次请求的身份（`flag:false` + `非法访问`）。
 *
 * 两种情况在这里长得一样，界面上只能同时给出「重试」与「重新登录」两个出口：
 * - 校内登录态失效（重登能修）
 * - 7 个身份字段没带全（我们自己的协议问题，重试/重登都没用，只能等修）
 *
 * 之所以要有独立类型：`code` 可能是 `"0"` 或 `"-100"`，光看 `code` 会把
 * `{"code":"0","flag":false,"msg":"非法访问"}` 当成成功，最后渲染成「暂无任务」——
 * 用户以为没有查寝安排，实际是请求被拒。
 */
class CheckinRejectedException(
    /** 服务端原文片段，只进「错误详情」，不进正文。 */
    val serverFragments: List<String> = emptyList(),
) : Exception("查寝请求被拒绝") {

    val technicalDetail: String
        get() = buildString {
            append("CHECKIN_REJECTED\n")
            append("阶段：fdygl 身份校验\n")
            append("响应：")
            append(serverFragments.filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "无" })
        }
}

/**
 * `fdygl` 的拒绝信号：`flag == false` 且 `msg` 含「非法访问」。
 *
 * 只看 `msg` 不看 `flag` 会误伤（别的业务失败也可能带这四个字）；
 * 只看 `flag` 又会把「flag:false 但没有 msg」的普通失败判成身份问题。
 */
internal fun isCheckinRejection(flag: Boolean?, msg: String?): Boolean =
    flag == false && msg?.contains("非法访问") == true
