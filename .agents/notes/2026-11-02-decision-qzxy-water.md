---
doc_type: decision
category: architecture
status: active
date: 2026-11-02
tags: [qzxy, water, bluetooth, network]
---

## 背景

接入趣智校园，控制本校饮水机（`bigTypeId=5`，经典蓝牙 SPP）与公寓热水表（`bigTypeId=4`，BLE GATT）。
协议依据是 `docs/接口分析/趣智校园分析报告.md` 第 9 节（真机 HAR + btsnoop 实证），
实施过程见 `docs/实施方案/2026-11-02-qzxy-water/`。这是会真实扣钱的功能，所以下面几条都是硬约束。

## 决定

- **趣智请求走独立 OkHttpClient**（`@QzxyOkHttpClient`，只挂 `QzxyInterceptor`）。主 client 的
  `AuthInterceptor` 会往 Form 里注入学校接口的 `access_token` 等字段，污染趣智请求。
- **趣智会话独立落盘**（DataStore `qzxy_session`），与学校账号互不影响：退出学校账号不清趣智会话。
- **签名是硬编码白名单，不是"全部参数升序"**：
  - `rateOrder` → `deviceId, randomNumber, telephone, xfModel`
  - `upload/data` → `loginCode, telephone, xfData`
  - `upload/fail` → `consumeDate, loginCode, telephone`
  `macType / bigTypeId / smallTypeId / protocolType / accountId` 不进签名。
  白名单集中在 `QzxyCrypto.SIGNED_*`，并由 `QzxyHarReplayTest` 用本地 HAR 逐条回放守卫。
- **`randomNumber` 每次重新取**：`rateOrder` 用开阀前那次 `0x23` 的值，`upload/data` 用关阀后
  另一次 `0x23` 的值，两者不是同一个；复用旧值服务端必拒。
- **`ActiveOrder` 不变量**（`WaterOrderRunner`）：
  - 没有成功的 `0x23` 绝不调 `rateOrder`；
  - `rateOrder` 成功到 `upload/data`（或 `upload/fail`）成功前，`ActiveOrder` 一直存在；
  - 上传失败保留 `ActiveOrder`，重试**重走整个结算流程**（重新取 `0x23` 的 randomNumber）；
  - `0x86` 写回失败只记日志，不影响结算结果（服务端已入账）。
- **`0x22` 关阀失败容忍**：设备可能已自行关阀，关阀应答不决定结算成败，继续走 `0x85` 采集。
- **传输层按 `bigTypeId` 选**：热水表走 `BleTransport`（`FF00`，写 `FF02` 每包 ≤20 字节，收 `FF01` 通知），饮水机走 `SppTransport`；帧协议、下单与结算编排共用。两者都有 btsnoop 实证（报告 §9.11），HTTP 字段里没有通道标志。
- **设备判据按 `bigTypeId`，不按名字**；`bigTypeId ∉ {4,5}` 或 `communicationTypeId != 0` 直接报不支持。
- `run`/`device` 帧协议、`0x23` 状态布局、`0x86` 写回长度全部照报告 §9.4–9.7，不做"优化"。

## 待真机确认（未写入代码承诺）

1. insecure / secure RFCOMM 哪种能连通、是否弹系统配对框。
2. 用户按设备实体键停水后再点「结束」，`0x22` 是否仍返回 `0x80`。
3. ~~出水中断开蓝牙，水是否继续出~~：热水表已确认断开后照常可用（官方 App 开阀约 4 分钟后主动断开）；饮水机未验证。
6. 自己的未结算单被下一位用户的 App 先读走上传后，再对该表做 `0x85` 会返回什么（目前读不到用量时报错并保留 `ActiveOrder`）。
4. 官方 App 被顶号后，LightXin 再次请求时的实际 `errorCode` 与 `errorMessage`。
5. 本校设备贴纸二维码的真实内容（`parseDeviceCode` 目前只认 `...,AABBCCDDEEFF` 与 12 位 hex）。

## 考虑过的替代方案

- **复用主 OkHttpClient + 白名单拦截器**：仍需让 `AuthInterceptor` 识别趣智域名并跳过，等价于新 client 但更脆。
- **热水表也走 SPP**：早期仅凭 HTTP 推断，11-02 btsnoop 证伪，热水表只有 BLE。
- **用服务端返回的 `clData` 写回设备**：官方 App 并不解密 `clData`，写回是固定 22 字节明文确认包。

## 后果

- 新增 `feature/water/{data,domain,ui}`、`core/network/QzxyInterceptor.kt`、`core/auth/QzxySessionStore.kt`、
  `core/designsystem/component/QrCodeScanner.kt`（从 AI 课堂抽出）。
- Manifest 增加 `BLUETOOTH` / `BLUETOOTH_ADMIN`（maxSdk 30）与 `BLUETOOTH_CONNECT`；连接已知 MAC 不需要扫描权限，所以 `SppTransport` 在 API 31+ 不调 `cancelDiscovery()`（它要求 `BLUETOOTH_SCAN`，未授权会抛 SecurityException）。
- 首页「附近设备」按需搜索（点了才申请权限，扫 8 秒）：`NearbyScanner` 同时做 BLE 扫描（热水表）与经典查询（饮水机），按广播名前缀 `KLCXKJ` 或 BLE 服务 `FEE7` 识别；设备全部同名，逐个 `deviceByMac` 查到名称才展示。API 31+ 申请 `BLUETOOTH_SCAN`（`neverForLocation`）+ `BLUETOOTH_CONNECT`，30 及以下用已声明的定位权限并要求系统定位开启。饮水机能否被经典查询搜到未实测。
- 账号被官方 App 顶号时会话失效，`QzxyRepository` 会清会话并跳登录页；登录页不额外解释。
