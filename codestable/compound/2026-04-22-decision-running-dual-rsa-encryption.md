---
doc_type: decision
category: constraint
date: 2026-04-22
slug: running-dual-rsa-encryption
status: active
area: 跑步数据上传
tags: [running, rsa, encryption, sports-api]
---

## 背景

跑步数据提交到服务端时需要加密。服务端接口的设计是字段名和字段值都要加密，且使用独立的跑步公钥（`publicKey2`），和登录用的 `publicKey` 不可混用。

## 决定

跑步数据上传时，字段名和字段值**都要**用 `publicKey2` 进行 RSA 加密。不能只加密值。

## 理由

服务端接口要求如此——抓包分析确认提交的请求体中 key 和 value 都是密文。这是服务端协议硬约束，不是客户端设计选择。

## 后果

- 新增跑步字段时必须也走 `RSAUtils.encryptSportData()` 加密
- `publicKey`（登录用）和 `publicKey2`（跑步用）不可混用，否则服务端解密失败
- 调试跑步接口时不能直接看明文参数，需要用私钥解密才能验证

## 相关文档

- `core/auth/RSAUtils.kt:36` — `encryptSportData()`
- `docs/接口分析/跑步接口深度分析报告.md`
- `codestable/architecture/network-overview.md` §6
