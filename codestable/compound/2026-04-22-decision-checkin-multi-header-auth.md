---
doc_type: decision
category: constraint
date: 2026-04-22
slug: checkin-multi-header-auth
status: active
area: 查寝接口
tags: [checkin, auth, header, fdygl]
---

## 背景

查寝接口（`fdygl.aiit.edu.cn`）的鉴权方式和校内主站不同，不是单一 Bearer token。服务端要求同时携带多个身份参数，缺任何一个都会返回 `-100 非法访问`。

## 决定

查寝接口请求必须同时携带以下 7 个身份字段：`accessToken`、`access_token`、`userCode`、`xh`、`userType`、`X-Requested-With`、`Origin`/`Referer`。这些字段由 `AuthInterceptor` 第三档自动注入（共 9 个 header 字段，含 `Authorization: Bearer`）。

## 理由

服务端接口硬约束。`docs/接口分析/查寝接口适配记录.md` 记录了踩坑过程——逐步补充字段才通过鉴权。

## 后果

- 新增查寝相关接口时，只要 host 是 `fdygl.aiit.edu.cn`，`AuthInterceptor` 会自动处理
- 修改 `AuthInterceptor` 时不能误删查寝档的任何字段
- `AuthInterceptor.kt` 顶部注释已过时（说"查寝不由 Interceptor 注入"），需要修正

## 相关文档

- `docs/接口分析/查寝接口适配记录.md` — 踩坑实录
- `core/network/AuthInterceptor.kt:54-69` — 查寝档实现
- `easysdd/architecture/network-overview.md` §2.4, §6
