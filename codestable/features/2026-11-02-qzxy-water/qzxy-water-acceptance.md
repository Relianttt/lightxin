---
doc_type: feature-acceptance
feature: qzxy-water
slug: qzxy-water
summary: 趣智校园登录 / 余额 / 找设备 / 经典蓝牙 SPP 开关阀与结算全流程落地；协议纯函数、网络层、订单编排、四页 UI 与导航已完成并通过单测与 lint，真机九项验证待实测
status: pending-verification
date: 2026-11-02
tags: [qzxy, bluetooth, water, network, retrofit]
---

# 趣智校园 饮水 / 热水 验收报告

> 阶段：验收闭环（阶段 3）
> 验收日期：2026-11-02
> 关联方案 doc：`qzxy-water-design.md`（本目录）
> 进度表：`qzxy-water-checklist.yaml`（本目录）

> 说明：本方案用的是"执行方案"结构（§0–§10），不是标准 design 的 §1–§4。下文按内容映射：接口契约 ← §3 协议 / §4 网络 / §5 编排；行为与决策 ← §1 范围 + §2 包结构 + §5.2 不变量 + §7 铁律；验收场景 ← §7.9 + §8。

## 1. 接口契约核对

**协议纯函数（§3）**
- [x] `QzxyCrypto.passwordDigest/smsSecret/sign`：与 §3.2 固定向量逐项一致（`4BC85B3E44`、`18cd33…`、`310b0d8e…`）
- [x] 签名白名单硬编码进 `QzxyCrypto.SIGNED_*`，`rateOrder` 4 字段 / `upload/data` 3 字段 / `upload/fail` 3 字段，与 §3.1、§9.10 一致
- [x] `QzxyFrame.encode/decodeLine`：`encode(0x23,[0])` == `"#60000480230000a316\n"`；大写/小写/带 `\r` 均可解；crc、尾字节、长度错一律 null
- [x] `parseStatus`：`p[19]/p[24..27]/p[28]/p[29..30]/p[11..12]` 取值与 §3.4 表一致；`<31` 字节抛 `UnsupportedProtocol`
- [x] `ackPayload` = `xf[1..18]` + `xf[19..22]` 反转，22 字节

**HAR 回放（§3.3）**
- [x] `QzxyHarReplayTest` 遍历两份 HAR 的 6 条 `rateOrder` / `upload/bluetooth/data`，按白名单重算签名 **6/6 命中**
- [x] 同测试断言请求字段集合等于 §4.4 末尾列出的 17 / 12 字段集合
- [x] 回放测试内不含任何抓包真实值（运行时读 HAR，HAR 缺失整测跳过）

**网络层（§4）**
- [x] `QzxyInterceptor` 注入 8 个公共参数（`loginCode/userId/accountId/projectId/telephone/telPhone/phoneSystem/version`），登录三接口不注入；同名字段不覆盖
- [x] `@QzxyOkHttpClient` 独立 client，**只挂** `QzxyInterceptor`，未复用主 `OkHttpClient`（未触发 `AuthInterceptor`）
- [x] 每个接口一个具体响应类，无泛型 `BaseResponse<T>`；响应类在 `feature/water/data/`，本地落盘对象在 `domain/`
- [x] 错误码映射：12/46/29/53/307/4004/170/226 + 其余透传 `errorMessage`；`success && errorCode==0` 才判成功

**编排（§5.2）**
- [x] `start`：`0x23` 成功 → `rateOrder` → 落 `ActiveOrder` → `0x21`；`0x21` 失败 → `upload/fail`，撤销成功清单、失败保留
- [x] `settle`：`0x23` → `0x22` → `0x85` → `upload/data` → `0x86` → 清单 + 记最近设备；`0x86` 失败不影响结果
- [x] 结算用的是**结算时那次** `0x23` 的 `randomNumber`（`WaterOrderRunnerTest` 用两次不同 random 断言）

## 2. 行为与决策核对

**范围（§1）**
- [x] 做了：账号登录（密码 / 短信）、余额、扫码 / 手动输入 / 最近设备、`bigTypeId ∈ {4,5}` 且 `communicationTypeId==0` 的 SPP 设备控制、结算、未结算恢复
- [x] 明确不做且确实没做：蓝牙搜索附近设备（grep `startDiscovery` 仅 `cancelDiscovery`）、`tcpDevice/*`、BLE GATT、MQTT、优惠券 / 广告 / 账单 / 充值、前台服务
- [x] 默认产品决定：入口在「更多功能」页（`MoreFeaturesScreen` 报修下方一行）；趣智账号与学校账号完全独立（`qzxy_session` DataStore，退出学校账号不清）；登录页不解释顶号

**关键决策落地**
- [x] D1 独立 OkHttpClient → `provideQzxyOkHttpClient` 只 add `QzxyInterceptor`
- [x] D2 签名白名单硬编码（不做全参数升序）
- [x] D3 `randomNumber` 每次重取（开阀 / 结算各一次）
- [x] D4 `ActiveOrder` 不变量：没有成功 `0x23` 不调 `rateOrder`；`rateOrder` 成功到上传成功前一直存在；上传失败保留可重试（重试重走全流程）
- [x] D5 设备判据按 `bigTypeId` 而非名称

**跨层纪律**
- [x] 错误语义：仓库层把 `IOException` 归一为「网络异常，请检查连接」，业务错误透传中文 message，会话失效抛 `QzxySessionExpired` 并清会话
- [x] 并发：`SppTransport.request` 用 `Mutex` 串行化，非目标 cmd 帧打日志丢弃
- [x] 可观测点：帧收发 `Log.d("Qzxy", "→/← …")`，扫码原文 `Log.i("Qzxy", …)`

**UI 铁律（§7.0）**
- [x] `feature/water/` 内 `MaterialTheme.colorScheme` / `Color(0x` / `LxTopBar` / `LxCard(` / `AlertDialog` / `Toast` 均 0 处
- [x] 颜色只用 token；动画只用 `LxMotion` 时长与曲线；金额一律 `formatLi/formatYuan`；无自造中文文案（除 §4.6/§5.2 允许的业务错误 message）

**挂载点反向核对（可卸载性）**
清单（本次改动命中的所有挂入位置）：

| 挂载点 | 落点 |
|---|---|
| M1 权限 | `AndroidManifest.xml`：`BLUETOOTH` / `BLUETOOTH_ADMIN`（maxSdk 30）、`BLUETOOTH_CONNECT`、`uses-feature bluetooth` |
| M2 域名常量 | `core/network/ApiConstants.kt`：`BASE_QZXY` / `QZXY_VERSION` / `QZXY_CONFIG_KEYS` |
| M3 DI | `core/network/NetworkModule.kt`：`@QzxyOkHttpClient` / `@QzxyRetrofit`；`feature/water/data/WaterModule.kt`：`QzxyApi` / `WaterTransport` / `WaterOrderApi` / `WaterOrderStore` / `WaterOrderRunner` |
| M4 拦截器 | `core/network/QzxyInterceptor.kt` |
| M5 会话 | `core/auth/QzxySessionStore.kt` |
| M6 扫码组件 | `core/designsystem/component/QrCodeScanner.kt`（从 AI 课堂抽出，AI 课堂改为调用它） |
| M7 输入组件 | `core/designsystem/component/ClaudeComponents.kt`：`LxInput` 增加可选 `keyboardActions` |
| M8 导航 | `navigation/Routes.kt`（4 条路由 + `waterDevice()`）、`navigation/NavGraph.kt`（4 个 composable + `water_refresh`） |
| M9 入口 | `feature/more/ui/MoreFeaturesScreen.kt`：`onNavigateWater` 参数 + 报修下的一行 |
| M10 AI 课堂 | `feature/aiclass/ui/AiClassScanScreen.kt`：改用 `QrCodeScanner` |

- [x] 反向 grep（`water|qzxy|BLUETOOTH_CONNECT|QrCodeScanner|KeyboardActions`，排除 `feature/water/`）命中：Manifest、ApiConstants、NetworkModule、QzxyInterceptor、QzxySessionStore、QrCodeScanner、ClaudeComponents、Routes、NavGraph、MoreFeaturesScreen、AiClassScanScreen —— 全在清单内，无漏记
- [x] 拔除沙盘推演：删 `feature/water/` + 逆向上述 10 处 → 回到引入前状态；`QrCodeScanner` 抽取与 `LxInput.keyboardActions` 属通用能力，保留无残留（AI 课堂继续使用）

## 3. 验收场景核对

对照 §7.9（UI 自查）与 §8（真机九项）：

**已完成**
- [x] **S1 协议正确性**：单测 + HAR 回放 6/6（证据：`./gradlew testDebugUnitTest`）
- [x] **S2 订单编排不变量**：`WaterOrderRunnerTest` 8 用例（证据：测试输出）
- [x] **S3 构建与静态检查**：`assembleDebug` / `assembleDebugAndroidTest` / `assembleRelease` / `lintDebug` 全通过，lint 0 error（证据：构建日志）
- [x] **S4 登录与会话**：真机会话建立成功（登录走密码还是短信未记录，执行者未参与该次操作），余额与官方 App 一致（¥9.11，project 506）；不同手机号属不同项目账户，余额以 `wallet` 返回为准（证据：真机截图 + 直接对照服务端响应）
- [x] **S5 首页 UI**：浅色 / 深色截图，余额面板、空态、手动输入弹层（空态 / 输入法弹出 / 无法识别设备号错误态）
- [x] **S6 登录页 UI**：密码态、验证码态 + 键盘（登录按钮在键盘上方）、手机号错误态（浅色 + 深色）
- [x] **S7 扫码组件抽取回归**：AI 课堂扫码页相机成功打开（`dumpsys media.camera` 显示本应用占用 Device 0）；相机预览 / 取景框 / 两处提示文案是从原文件原样搬移（未改写代码），页面外壳仍是 `Scaffold + LxTopBar`；权限占位文案有差异（见 checklist note）
- [x] **S8 release 包可用（部分）**：`app-release.apk` 覆盖安装后启动正常、无 R8 引发的崩溃，旧的学校会话与趣智会话均保留，趣智余额正常拉取（证明 R8 下 Gson 能解析 `wallet` 响应）；**未在 release 包上重新登录**，登录响应解析与开阀链路仍待 §8 验证

证据截图（本地未入库，含个人信息）：`docs/实施方案/2026-11-02-qzxy-water/screenshots/`

**待真机（明天实测，§8 第 2–8 项）**
- [ ] **S9** insecure / secure RFCOMM 哪种能连、是否弹配对框
- [ ] **S10** 饮水机完整一单：开始 → 出水 → 结束 → 扣费与官方账单一致
- [ ] **S11** 设备实体键停水后再点「结束」，`0x22` 是否仍回 `0x80`
- [ ] **S12** 出水中离开页面（断蓝牙），水是否继续出；回来后能否从未结算卡片结算
- [ ] **S13** 出水中杀进程，重进后可结算
- [ ] **S14** 热水表（`bigTypeId=4`）跑一单，确认 `0x86` 写回被接受
- [ ] **S15** 官方 App 顶号后的提示与 `errorCode`（回填 §4.6）
- [ ] **S16** 设备页 `Idle` / `Connecting` / `Running` / `NeedSettle` / 结算弹层 / 不支持设备 的浅色与深色截图

> 结论：静态与单测层面全部通过；**涉及真机资金、蓝牙与设备状态机的场景尚未验证**，因此本报告状态为 `pending-verification`，checklist 中真机步骤保持 `blocked`。

## 4. 术语一致性

- `QzxyInterceptor` / `QzxySessionStore` / `QzxyRepository` / `QzxyCrypto` / `QzxyFrame` / `SppTransport` / `WaterOrderRunner` / `WaterLocalStore`：与方案 §2 包结构文件名逐一对应
- 业务实体：`WaterDevice` / `DeviceStatus` / `ActiveOrder` / `SettleResult` / `RecentDevice` / `RateOrderInfo`，字段名与方案 §2/§5 一致
- 状态机：`DevicePhase` 10 个状态与方案 §7.7 表一致；`StartOutcome.Running/NeedSettle` 与 §5.2 一致
- 防冲突：未复用学校侧 `SessionManager` / `TokenManager` 命名，趣智一律 `Qzxy*` 前缀，无同名混淆

## 5. 架构归并

- [x] 新增 [`water-overview.md`](../../architecture/water-overview.md)：术语、四层结构（协议 / 网络 / 传输与编排 / UI）、帧协议与签名白名单、`ActiveOrder` 不变量、真机待验项
- [x] [`network-overview.md`](../../architecture/network-overview.md)：新增「第三套 OkHttp 栈」与 `@QzxyOkHttpClient` / `@QzxyRetrofit`，qualifier 计数 12 → 14（12 Retrofit + 2 OkHttpClient），补消费映射
- [x] [`auth-overview.md`](../../architecture/auth-overview.md)：补 `QzxySessionStore`（`qzxy_session` DataStore，与校内 `lightxin_auth` 完全独立，退出学校账号不清）
- [x] [`navigation-overview.md`](../../architecture/navigation-overview.md)：补 4 条 water 路由、入口位置、「任意页 `QzxySessionExpired` → 登录页」与 `water_refresh` 回刷约定
- [x] [`designsystem-overview.md`](../../architecture/designsystem-overview.md)：补 `QrCodeScanner`（从 AI 课堂抽出，权限 + 预览 + 取景框）、`LxInput.keyboardActions`
- [x] [`DESIGN.md`](../../architecture/DESIGN.md)：模块索引加 `water` 行、术语表加趣智条目、network/auth 条目计数更新、架构文档清单加 `water-overview.md`
- [x] 主题合并（§10）无需新架构 doc：属于 `designsystem-overview.md` 已有的「主题变体数量」约束，已在其中注明只有 `values/` + `values-night/` 两份

## 6. requirement 回写

- [ ] **未落档**。`codestable/requirements/` 目前是空目录，本次没有可依据的需求素材（用户明确要求不要凭猜写用户故事和"为什么需要"），起草过一版后已删除，方案 frontmatter 保持 `requirement: null`
- 结论：等功能在真机跑通、且你愿意口述一次"这东西是为什么加的 / 边界在哪"之后再 `cs-req` backfill；在那之前 requirements 保持空

## 7. roadmap 回写

- [x] 方案 frontmatter 无 `roadmap` / `roadmap_item` 字段 → 非 roadmap 起头，跳过

## 8. AGENTS.md / CLAUDE.md 候选盘点

- [x] 无候选。本次的环境类坑（release 装不上的 unsigned 包、adb `input text` 被中文输入法吞字）属于一次性或工具层问题，已分别落在 build.gradle 注释与验收报告，不写入 AGENTS.md

## 9. 遗留

**已知限制**
1. 真机九项未验（见 §3 S9–S16），其中「断连是否停水」可能反过来要求设备页离开前先结算
2. 28 字节状态布局不支持，直接报「暂不支持此设备」
3. 本校贴纸二维码内容未确认，`parseDeviceCode` 目前只认 `…,AABBCCDDEEFF` 与 12 位 hex（含冒号 / 小写）
4. `bluetooth/fail` 未抓包（等级 B），只在 `0x21` 失败路径调用
5. 扫码页标题使用 `LxNavBar`（`§7.0` 禁 `LxTopBar`），与原 AI 课堂页顶栏样式略有差异

**顺手发现（未处理）**
- `SppTransport.close()` 与 `request()` 无锁竞争保护：极端情况下关连接时可能抛一次 IOException，被上层归为「与设备断开连接」，不影响结算重试
- `hexToBytes` 的 `require` 文案是开发期异常信息，若真触发会以原始英文露出

**后续可选**
- 蓝牙搜索附近设备（`SearchDeviceListActivity` 那套）等真机拿到广播名后再做
- `upload/data` 的 `clData` 仍未使用（官方 App 也不用），无需求不解析
