---
doc_type: architecture
slug: water-overview
scope: 趣智校园（饮水机 / 公寓热水表）接入：独立会话与 OkHttp 栈、协议签名与帧编解码、经典蓝牙 SPP 传输、开阀/结算编排、四页 UI
summary: water 用独立 OkHttpClient + QzxyInterceptor 接 v3-api.china-qzxy.cn，帧走 RFCOMM SPP（#HEX 文本帧）；WaterOrderRunner 编排「0x23 查状态 → rateOrder → 0x21 写费率 → 0x22 → 0x85 → upload/data → 0x86」，用落盘的 ActiveOrder 保证不丢单
status: current
last_reviewed: 2026-11-02
tags: [qzxy, water, bluetooth, spp, rfcomm, retrofit]
depends_on: [network-overview, auth-overview, navigation-overview, designsystem-overview]
---

# 趣智校园（饮水 / 热水）总览

## 0. 术语

- **趣智会话** — 趣智账号自己的 `loginCode / userId / accountId / projectId / telephone`，落 `qzxy_session` DataStore，与校内 `lightxin_auth` 完全独立
- **公共参数** — 每个趣智请求都带的 8 个字段：`loginCode / userId / accountId / projectId / telephone / telPhone / phoneSystem=android / version=6.5.28`
- **签名白名单** — 参与签名的字段是硬编码子集，不是"全部参数升序"；锚点 `QzxyCrypto.SIGNED_*`
- **帧** — 设备侧文本协议 `'#' + HEX + '\n'`，请求方向 `0x80`、应答方向 `0x81`
- **ActiveOrder** — 已开阀未结算的订单，落 `qzxy_water` DataStore，App 被杀后可恢复结算
- **力度单位厘** — 协议金额单位，1 元 = 1000 厘；UI 一律经 `formatLi/formatYuan`

## 1. 定位与受众

`feature/water/` 负责：趣智账号登录（密码 / 短信）、余额、找设备（扫码 / 手输设备号 / 最近设备）、经典蓝牙 SPP 开关阀、结算、未结算恢复。

读者：改趣智协议 / 帧格式 / 订单流程，或排查"开阀失败""结算丢单""蓝牙连不上"的人。

## 2. 结构与交互

### 2.1 四层

| 层 | 组件 | 职责 |
|---|---|---|
| 协议纯函数 | `data/QzxyCrypto`、`data/bt/QzxyFrame` | md5 / 密码摘要 / 短信 secret / 签名；帧编解码、`0x23` 状态解析、`0x86` 写回载荷 |
| 网络 | `core/network/QzxyInterceptor` + `@QzxyOkHttpClient` / `@QzxyRetrofit`、`data/QzxyApi`、`data/QzxyResponses`、`data/QzxyRepository` | 公共参数与请求头注入、每个接口一个具体响应类、错误码映射与会话失效处理 |
| 传输与编排 | `data/bt/SppTransport`、`domain/WaterOrderRunner`、`data/WaterLocalStore` | RFCOMM 连接与收发；开阀 / 结算编排；未结算订单与最近设备落盘 |
| UI | `ui/QzxyLoginScreen`、`WaterHomeScreen`、`WaterScanScreen`、`WaterDeviceScreen`、`WaterViewModels` | 登录、余额与入口、扫码、设备页状态机 |

`domain/WaterOrderRunner` 只依赖 `WaterTransport` / `WaterOrderApi` / `WaterOrderStore` 三个接口（Hilt 分别绑到 `SppTransport` / `QzxyRepository` / `WaterLocalStore`），因此编排逻辑可用假实现单测。

### 2.2 鉴权与请求形态

- `AuthInterceptor` **不参与**趣智请求：`@QzxyOkHttpClient` 只挂 `QzxyInterceptor`（主 client 会往 Form 注入校内 `access_token` 等字段，污染趣智请求）
- 请求头：`User-Agent: okhttp/4.2.2`；有会话时追加 `Config-Project: {projectId}`、`Config-Keys: module_list,advertise_type,…`
- 公共参数按 §0 注入：GET 追加到 query、FormBody 追加到 body，同名字段不覆盖；`/user/login`、`/user/verification/code/get`、`/user/registerAndLogin` 不注入
- 判成功只看 body（`success == true && errorCode == 0`），HTTP 一律 200；会话失效（message 含「登录/失效/过期/请重新」或 `errorCode 8`）清会话抛 `QzxySessionExpired`，UI 回登录页

### 2.3 帧协议与设备命令

```
请求帧: 60 00 len 80 cmd 00 data… crc 16      len = data.size + 3
应答帧: 60 00 len 81 cmd 00 data… crc 16      crc = (Σdata + frame[3] + cmd) & 0xFF
```

| cmd | 用途 | 请求 data | 应答 |
|---|---|---|---|
| `0x23` | 查状态 | `[0x00]` | ≥31 字节，`p[19]` protocolType、`p[24..27]` randomNumber、`p[28]` state、`p[29..30]` macType、`p[11..12]` acct |
| `0x21` | 写费率 | `hex(rateOrder.downData)` | `0x80` |
| `0x22` | 关阀 | `[0x00]` | `0x80`（设备可能已自行关阀，应答不决定结算成败） |
| `0x85` | 采集 | `[0x00]` | 42 字节 `xfData`（原样上传） |
| `0x86` | 写回 | `xf[1..18] + reverse(xf[19..22])`（22 字节） | `0x80`（失败只记日志） |

`0x23` 的 `randomNumber` **每次都要重取**：`rateOrder` 用开阀前那次，`upload/data` 用关阀后另一次，复用旧值服务端必拒。

### 2.4 订单编排（`WaterOrderRunner`）

```
start:  connect → 0x23 → state==0 → rateOrder → 落 ActiveOrder → 0x21
                                                    └ 失败 → upload/fail（撤销成功则清单，失败保留）
        state≠0 且 acct==本人 → NeedSettle          state≠0 且非本人 → 「设备使用中」

settle: connect → 0x23（取新 randomNumber）→ 0x22 → 0x85 → upload/data → 0x86 → 清单 + 记最近设备
```

不变量：没有成功的 `0x23` 绝不调 `rateOrder`；`rateOrder` 成功到 `upload/data`（或 `upload/fail`）成功前 `ActiveOrder` 一直存在；上传失败保留 `ActiveOrder`，重试**重走整个 settle**（重新取 randomNumber）。

### 2.5 设备页状态机

`Loading / LoadFailed / Unsupported / Idle / Connecting / Starting / Running / Settling / NeedSettle / BusyByOther`，按钮文案与返回拦截见设计 §7.7 表；`Connecting/Starting/Settling` 拦截返回，`Running` 返回前弹「还在使用中」确认。进入页面不自动连蓝牙，点按钮才连；点按钮前在 Screen 层做权限 / 适配器 / 开关检查。

### 2.6 UI 约定

- 只用 Claude 层组件与 token；`QrCodeScanner` 为通用扫码组件（权限 + 预览 + 取景框），AI 课堂与趣智共用
- 金额 `formatLi`（厘）/`formatYuan`（服务端元字符串），`tnum` 数字特性；设备标题 `类型 · 房间`，副标题 `园区 · 楼 · 层`

## 3. 数据与状态

| DataStore | 内容 | 生命周期 |
|---|---|---|
| `qzxy_session` | `telephone / login_code / user_id / account_id / project_id / name` | 趣智登录写入；退出趣智或会话失效清除；**退出学校账号不清** |
| `qzxy_water` | `active_order`（Gson JSON）、`recent_devices`（最多 5 台，最近在前，含房间/楼栋） | App 内长期保留 |

账号顶号：官方 App 登录会顶掉本 App 会话，反之亦然（协议层单会话），因此「会话失效 → 回登录页」是常规路径而非异常。

## 4. 关键决策

- 独立 OkHttpClient + 独立 DataStore（不复用主 client / 校内 token）
- 签名白名单硬编码；`randomNumber` 每次重取
- `ActiveOrder` 落盘保证「预扣了但没结算」可恢复；任何异常路径 `transport.close()`
- 设备判据 `bigTypeId ∈ {4,5}` + `communicationTypeId == 0`，不按设备名

## 5. 代码锚点

- `app/src/main/java/com/lightxin/feature/water/data/QzxyCrypto.kt`、`data/bt/QzxyFrame.kt`
- `app/src/main/java/com/lightxin/core/network/QzxyInterceptor.kt`、`core/network/NetworkModule.kt`（`@QzxyOkHttpClient` / `@QzxyRetrofit`）
- `app/src/main/java/com/lightxin/feature/water/data/QzxyRepository.kt`
- `app/src/main/java/com/lightxin/feature/water/data/bt/SppTransport.kt`、`domain/WaterOrderRunner.kt`
- `app/src/main/java/com/lightxin/feature/water/ui/*`、`core/designsystem/component/QrCodeScanner.kt`
- 单测：`app/src/test/java/com/lightxin/feature/water/`（含 HAR 回放）

## 6. 已知约束 / 边界情况

- 只支持实测过的 ≥31 字节 `0x23` 布局；28 字节布局直接报「暂不支持此设备」
- 只连已知 MAC，不做 discovery → 不需要 `BLUETOOTH_SCAN` 与定位权限；API 31+ 需 `BLUETOOTH_CONNECT`
- 本校贴纸二维码内容未确认；`parseDeviceCode` 认 `…,AABBCCDDEEFF` 与 12 位 hex（可带冒号 / 小写）
- `bluetooth/fail` 未抓包（等级 B），只在 `0x21` 失败路径调用
- 待真机确认：insecure/secure RFCOMM 哪种可连；设备键停水后 `0x22` 应答；断开蓝牙是否停水；热水表 `0x86` 写回；顶号后实际 `errorCode`
- 协议与真机证据来源：`docs/接口分析/趣智校园分析报告.md` 第 9 节；决策记录 `.agents/notes/2026-11-02-decision-qzxy-water.md`

## 7. 相关文档

- 方案与验收：`codestable/features/2026-11-02-qzxy-water/`
- `network-overview.md`、`auth-overview.md`、`navigation-overview.md`、`designsystem-overview.md`
- 接口分析报告：`docs/接口分析/趣智校园分析报告.md`
