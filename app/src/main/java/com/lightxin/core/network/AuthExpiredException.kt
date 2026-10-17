package com.lightxin.core.network

/**
 * 校内登录态已失效 —— 与 [FifUserMappingException] 是两码事，不要混用。
 *
 * 触发面有两类，处置都是「重新登录」，但不能让界面把它们说成「AI课堂账号关联异常」：
 * - HTTP 401（各接口通用）
 * - 服务端把鉴权失败塞进 200 响应体：劳动教育 `ldjy` 回 `{"flag":false,"result":"非法访问"}`
 *
 * 注意 `fdygl` 查寝的 `-100 非法访问` **不是**登录失效，而是 7 个身份字段没带全，
 * 那条路径由 `CheckinRepository` 自己处理，别把这里的判定套到它头上。
 */
class AuthExpiredException(
    message: String = "登录已失效，请重新登录",
    /** 只进「错误详情」弹窗的结构化说明，绝不进正文。 */
    val technicalDetail: String = "",
) : Exception(message)

/** 是否属于「校内登录失效」。沿 cause 链判断，避免被仓库包一层后丢掉类型。 */
fun Throwable.isAuthExpired(): Boolean {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is AuthExpiredException) return true
        current = current.cause
    }
    return false
}
