---
doc_type: issue-fix
issue: 2026-04-28-qr-checkin-session-expired
status: fixed
severity: P1
summary: 扫码签到"AI课堂登录态已失效"——修复 Cookie 身份信息缺失与重试条件不匹配
tags: [aiclass, checkin, cookie, session, sso]
---

# 扫码签到"AI课堂登录态已失效" Fix Note

## 根因回顾

轻小信 `qrcodeHandler` 请求的 Cookie 头仅含 `SESSION`，缺少 `id`、`studentId`、`currentUserName` 三个身份 Cookie，服务端无法识别用户 → 302 重定向到 `h5/login` 登录页。

两个直接原因：

1. **身份 Cookie 来源单一**：`buildQrCookieHeader()` 从 DataStore 读取 `studentId`/`userName` 拼入 Cookie 头，但 DataStore 可能为空（SSO 部分成功或值被清），导致身份 Cookie 缺失。OkHttp cookie jar 中也无这些值（`establishSession()` 只设置了 `SESSION`）

2. **重试条件写错**：`requiresQrSessionRefresh()` 匹配 `"loginManage"`，但服务端实际返回 Location 为 `/iplat-pass-aiit/h5/login`（不含 "Manage"），重试从未触发

## 修改清单

### 1. `AiClassRepository.kt:290-291` — 修复重试触发条件

```diff
- result.code == 302 && result.location.contains("loginManage", ignoreCase = true)
+ result.code == 302 && result.location.contains("login", ignoreCase = true)
```

### 2. `FifSessionManager.kt:117, 176-189` — SSO 成功后同步身份 Cookie 到 OkHttp jar

新增 `saveIdentityCookies()` 方法，在 `performSso()` 成功持久化 DataStore 后调用，将 `id`、`studentId`、`currentUserName` 写入 OkHttp cookie jar。`buildQrCookieHeader()` 原本就会从 jar 中读取 Cookie，现在 jar 中有了身份信息，即使 DataStore 读取失败也能从 jar 中兜底。

```kotlin
private fun saveIdentityCookies(studentId: String, userName: String) {
    val fifUrl = ApiConstants.BASE_FIF.toHttpUrl()
    val cookies = listOfNotNull(
        Cookie.Builder().name("id").value(studentId).domain("fifedu.com").path("/").build()
            .takeIf { studentId.isNotBlank() },
        Cookie.Builder().name("studentId").value(studentId).domain("fifedu.com").path("/").build()
            .takeIf { studentId.isNotBlank() },
        Cookie.Builder().name("currentUserName").value(userName).domain("fifedu.com").path("/").build()
            .takeIf { userName.isNotBlank() },
    )
    if (cookies.isNotEmpty()) {
        cookieJar.saveFromResponse(fifUrl, cookies)
    }
}
```

## 修复策略说明

采用**双重保障**策略：

| 层级 | 来源 | 说明 |
|---|---|---|
| 主路径 | DataStore | SSO 成功后的持久化值（原有逻辑） |
| 兜底路径 | OkHttp cookie jar | SSO 成功后同步写入（本次新增），`buildQrCookieHeader()` 读取 jar 时直接命中 |

即使 DataStore 因未知原因读不到值，cookie jar 中的身份 Cookie 也能被 `buildQrCookieHeader()` 拼入 Cookie 头。

## 验证建议

1. 清除轻小信 app 数据，重新登录
2. 进入 AI 课堂 → 扫码签到
3. 确认提示"扫码签到成功"而非"AI课堂登录态已失效"
4. 在 Reqable 中抓包确认 `qrcodeHandler` 请求的 Cookie 头包含 `id`、`studentId`、`currentUserName`、`SESSION` 四个 Cookie
