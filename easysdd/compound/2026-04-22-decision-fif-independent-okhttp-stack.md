---
doc_type: decision
category: architecture
date: 2026-04-22
slug: fif-independent-okhttp-stack
status: active
area: 网络层 / AI 课堂
tags: [network, fif, okhttp, sso, cookie]
---

## 背景

AI 课堂接入第三方教学平台 FIF（`sttp.fifedu.com`），需要独立 SSO 登录态。校内主站使用 `access_token` 体系，FIF 使用 `authorization: Basic <token>` + `SESSION` Cookie，两者身份体系完全不互通。

## 决定

FIF 使用独立的 `OkHttpClient` + `CookieJar`，不与校内主站共享拦截器链。

## 理由

1. FIF 身份字段（`authorization` / `SESSION` Cookie）和校内主站（`access_token` / `userCode`）不互通，共用 `AuthInterceptor` 会把校内参数错误注入到 FIF 请求
2. FIF SSO 需要手动处理 302 Location 提取 token（`followRedirects(false)`），和校内默认栈的自动重定向行为冲突
3. 独立 CookieJar 保证 FIF 的 SESSION Cookie 不会和校内主站可能的 Cookie 混在一起

## 考虑过的替代方案

- 共享 OkHttpClient + 在 AuthInterceptor 里按 host 路由：理论上可行，但 FIF 的 SSO 三步握手（302 手动跟随、Cookie 管理、会话有效性判定）和校内链路差异太大，强行统一会让拦截器逻辑急剧膨胀

## 后果

- 新增一个 `@FifOkHttpClient` Qualifier + 独立的 `CookieJar`
- FIF 会话有效性需要自行判定（`FifSessionManager.isSessionValid()`），不能复用 `TokenRefreshInterceptor` 的 401 自动刷新
- 退出登录时校内主站和 FIF 会话是两条独立清理路径

## 相关文档

- `easysdd/architecture/network-overview.md` §2.1, §2.6
- `docs/项目规划/AI课堂开发规划.md` §技术方案
- `core/network/FifSessionManager.kt`
