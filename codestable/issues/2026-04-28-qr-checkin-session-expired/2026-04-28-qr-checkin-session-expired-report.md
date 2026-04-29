---
doc_type: issue-report
issue: 2026-04-28-qr-checkin-session-expired
status: confirmed
severity: P1
summary: 轻小信扫码签到时 qrcodeHandler 请求缺少 id/studentId/currentUserName 身份 Cookie，服务端判定登录态失效
tags: [aiclass, checkin, cookie, session, sso]
---

# 扫码签到"AI课堂登录态已失效" Issue Report

## 1. 问题现象

在轻小信中打开 AI 课堂扫码签到功能，扫描课堂二维码后，弹出提示"扫码签到失败：AI课堂登录态已失效"，签到未完成。同一时间、同一课堂用安小信扫码签到正常。

HAR 抓包对比：

- **轻小信**：`qrcodeHandler` 请求 → 服务端 302 重定向到 `https://pass.fifedu.com/iplat-pass-aiit/h5/login`（登录页）
- **安小信**：`qrcodeHandler` 请求 → 服务端 302 重定向到 `https://sttp.fifedu.com/studycenter-nh5/mobileinteraview/signSuccess.html?signId=...`（签到成功）

## 2. 复现步骤

1. 打开轻小信，进入 AI 课堂模块
2. 点击扫码签到，扫描课堂二维码
3. 观察到：弹出"扫码签到失败：AI课堂登录态已失效"

复现频率：稳定复现（每次扫码均出现）

## 3. 期望 vs 实际

**期望行为**：扫描二维码后，服务端返回 302 跳转到 `signSuccess.html`，客户端识别到签到成功，UI 显示"扫码签到成功"提示。

**实际行为**：扫描二维码后，服务端返回 302 跳转到 `/iplat-pass-aiit/h5/login` 登录页，客户端匹配到 `login` 关键字，判定为"AI课堂登录态已失效"，弹出错误提示。

## 4. 环境信息

- 涉及模块 / 功能：AI 课堂 — 扫码签到
- 相关文件 / 函数：
  - `app/.../aiclass/data/AiClassRepository.kt` — `submitQrCode()`、`buildQrCookieHeader()`、`executeQrCodeRequest()`
  - `app/.../core/network/FifSessionManager.kt` — `performSso()`、`fetchUserMapping()`、`appendQrLoginParams()`
- 运行环境：Android 真机，轻小信 dev 版本
- 其他上下文：安小信（原版）同一场景正常；抓包文件见 `D:/Document/lightxin/har/Ai课堂登录态已失效.har` 和 `D:/Document/lightxin/har/AI课堂扫码签到.har`

## 5. 严重程度

**P1 - 严重** — 扫码签到是 AI 课堂核心功能，完全无法使用，但有安小信作为绕过方法。需尽快修复。

## 备注

HAR 抓包关键发现：轻小信请求的 Cookie 头仅含 `SESSION=b5c71f5e-...`，缺少 `id`、`studentId`、`currentUserName` 三个身份 Cookie。安小信成功请求中完整携带了这四个 Cookie（`id=3889526`、`studentId=3889526`、`currentUserName=aiit3233032235`、`SESSION=15aa94ff-...`）。此外轻小信还额外携带了 `authorization: Basic <JWT>` 头，安小信无此头——说明两者的请求构造方式完全不同（轻小信 OkHttp 原生 vs 安小信 WebView）。
