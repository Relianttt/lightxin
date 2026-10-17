---
doc_type: architecture
slug: running-overview
scope: 跑步模块的整体架构，覆盖真实 GPS 跑步、前台 Service 跟踪、模拟提交、路线模板录制与管理、大一大二智慧运动分流、俱乐部/锻炼考勤、以及跑步数据加密上传链路
summary: running 以 RunningRepository + RunningTracker 为主干，把真实跑步、模拟提交、模板录制收束到 RunningSnapshot 上传模型；模拟轨迹统一经 PolylineSampler 插值并由 GeneratedTrajectory 携带坐标系标记；智慧运动入口按 checkDsStudent.result 分流，大一大二读取 extraInfo/extraDetailInfo 与 clubInfo，上传成功需同时满足 result=OK 和有效非 0 id
status: current
last_reviewed: 2026-10-13
tags: [running, gps, service, route-template, rsa, sports, grade, club, trajectory-sampling]
depends_on: [network-overview]
---

# 跑步模块总览

## 0. 术语

- **真实跑步会话** — 先向服务端 `startRunning.do` 申请 `exerciseId`，再由前台 `RunTrackingService` 持续采集 GPS，结束时上传 `RunningSnapshot`
- **模板录制会话** — 不申请 `exerciseId`，只复用 `RunningTracker + RunTrackingService` 采点，结束后经质量校验存成 `RouteTemplate`
- **路线模板** — 保存在 `route_templates.json` 的本地轨迹资产；可设默认、重命名、删除，并在模拟提交时驱动 `PolylineSampler`
- **上传快照** — `RunningSnapshot`，是“可上传一次跑步”的统一中间态；真实跑步与模拟提交都先归一成它再走加密上传
- **距离计算** — `GeoDistance.metersBetween()` 提供统一 Haversine 米制距离，供 GPS 采集、模板采样和路线质量校验复用
- **年级分流** — 智慧运动以 `checkDsStudent.do` 返回的 `result` 为唯一分流依据；包含“大一大二”时走课外跑步/俱乐部接口，其他维持大三大四 startIndex 路径
- **俱乐部摘要/任务** — 大一大二分支的 `ClubSummary` 来自 `mobile/index/clubInfo.do`，俱乐部详情任务来自 `mobile/auto/clubInfo.do`
- **锻炼考勤** — 俱乐部活动任务的本地二维码 + `getQrcodeResult.do` 轮询确认流程
- **有效上传 ID** — 跑步上传返回必须同时满足 `result=OK` 且 `id` 非空、非 `"0"`；否则按失败处理，避免 UI 显示“成功”但今日里程仍为 0
- **轨迹锁定** — `stopCollecting()` 把 `endTimeMillis` 写进 `RunningTrackerState`，此后不再接受新 GPS 点，重试上传用的仍是锁定时的距离与时长

## 1. 定位与受众

本 doc 覆盖 `feature/running/` 的完整主链，不只解释“怎么发请求”，而是解释这三条路径如何共用同一套状态与上传模型：

- 真实跑步：开始跑步 -> GPS 跟踪 -> 结束上传
- 模拟提交：参数校验 -> 生成轨迹 -> 上传
- 模板系统：录制模板 -> 质量校验 -> 本地保存 -> 驱动模拟

适用读者：

- 要改跑步上传链路、加密逻辑或定位策略的人
- 要继续扩路线模板系统的人
- 要维护大一大二智慧运动、俱乐部详情或锻炼考勤的人
- 在首页、我的页或导航里接跑步入口的人

## 2. 结构与交互

### 2.1 一套核心模型，三条业务路径

真实跑步、模拟提交、模板录制共用一套领域模型：

- `TrackPoint` 表示单个轨迹点
- `RunningTrackerState` 表示当前采集中的会话状态
- `RunningSnapshot` 表示“已准备上传”的一次跑步
- `RouteTemplate` 表示可持久化、可复用的本地路线资产

锚点：`feature/running/domain/RunningModels.kt:24-67` / `feature/running/domain/RouteTemplate.kt:3-18`。

这样分的好处是：采集、模板、上传不需要互相转换多套中间结构。真实跑步产出的点、模板录制产出的点、模拟生成的点，最终都能落回 `List<TrackPoint>`。

### 2.1.1 智慧运动年级分流与大一大二摘要

`RunningRepository.getDashboard()` 先读取 `checkDsStudent.do` 的 `result`，并通过 `SportsGradeMapper.isJuniorGrade()` 判断是否为大一大二。大三大四仍读取 `startIndex.do` 的任务目标；大一大二并行读取：

- `extraInfo.do`：课外跑步任务主信息，提供 `extraId / planMile / completeMile`
- `extraDetailInfo.do`：今日/累计/剩余里程、`memberId`、单次目标等细节
- `index/clubInfo.do`：俱乐部摘要卡片

大一大二目标里程优先使用 `extraInfo.planMile/completeMile`；当 `extraInfo` 临时失败但 `extraDetailInfo` 成功时，可用 `completeMile + surplusMile` 兜底目标，保持接口独立降级。UI 是否显示俱乐部卡直接看 `RunningDashboard.clubSummary != null`，不在 UI 层散落年级字符串判断。

锚点：`feature/running/data/RunningRepository.kt:34-100` / `feature/running/data/SportsGradeMapper.kt:23-56` / `feature/running/domain/RunningModels.kt:3-20`。

### 2.1.2 俱乐部详情与锻炼考勤

俱乐部详情不膨胀 `RunningViewModel`，而是由独立 `ClubDetailViewModel` 调 `RunningRepository.getClubDetail()`，解析 `auto/clubInfo.do` 的任务时间线与 `checkList` 历史。锻炼考勤由 `ExerciseCheckViewModel` 生成本轮 timestamp 与二维码内容，并每 1.5 秒轮询 `getQrcodeResult.do`，60 秒超时。

锚点：`feature/running/exercise/ui/ClubDetailViewModel.kt:21-38` / `feature/running/exercise/ui/ExerciseCheckViewModel.kt:44-91` / `feature/running/data/RunningRepository.kt:128-155`。

### 2.2 真实跑步主链

真实跑步链路由 `RunningViewModel + RunningTracker + RunTrackingService + RunningRepository` 共同完成：

1. `RunningHomeScreen` 请求定位/通知权限后，调用 `viewModel.startRealRun()`
2. `RunningRepository.startRunning()` 向服务端申请 `exerciseId/memberId`
3. 成功后 `tracker.beginSession(startInfo)` 建立本地会话，并由 `RunTrackingService.start(context)` 开始前台跟踪
4. Service 从 `LocationProvider.locationUpdates()` 订阅 GPS，并把位置持续喂给 `tracker.onLocationUpdate(location)`
5. 在 `RunningActiveScreen` 点击结束后，先 `RunTrackingService.stop(context)`，再由 `viewModel.finishRealRun()` 取 `snapshotForUpload()` 并上传

锚点：`feature/running/ui/RunningHomeScreen.kt:68-98` / `feature/running/ui/RunningViewModel.kt:115-157,293-324` / `feature/running/data/RunningRepository.kt:74-105,137-202` / `feature/running/service/RunTrackingService.kt:49-78` / `feature/running/service/RunningTracker.kt:24-118` / `core/location/LocationProvider.kt:42-69` / `feature/running/ui/RunningActiveScreen.kt:73-89`。

### 2.3 `RunningTracker` 是真实跑步与模板录制的共享采集核心

`RunningTracker` 是 running 模块最关键的共享状态机。它不直接依赖网络、UI 或模板存储，只做三件事：

- 建立会话：`beginSession()` 用于真实跑步，`beginTemplateSession()` 用于模板录制
- 接收定位：`onLocationUpdate()` 计算增量距离、过滤异常段、追加轨迹点
- 产出快照：`snapshotForUpload()` 只对真实跑步开放，因为它要求 `startInfo != null`

锚点：`feature/running/service/RunningTracker.kt:24-43,53-118`。

当前轨迹过滤规则也集中在这里：

- `< 1.5m` 的段视为原地抖动，丢弃
- `> 120m` 的单段视为异常跳点，丢弃

锚点：`feature/running/service/RunningTracker.kt:63-89`。

距离计算不在 `RunningTracker` 内重复实现，而是调用 `GeoDistance.metersBetween()`。同一个工具也被 `PolylineSampler` 和 `RouteQualityChecker` 复用，避免真实跑步、模拟路线和模板质量校验出现三套距离口径。

锚点：`feature/running/domain/GeoDistance.kt:9-18` / `feature/running/service/RunningTracker.kt:63` / `feature/running/domain/PolylineSampler.kt:72` / `feature/running/domain/RouteQualityChecker.kt:27`。

### 2.4 前台 Service 只负责采集，不负责上传

`RunTrackingService` 的职责被刻意收窄：

- 开前台通知
- 订阅 `LocationProvider` 的 GPS Flow
- 把点推给 `RunningTracker`
- 在 `STOP` / `CANCEL` 时通知 tracker 停采或清会话

它**不**直接做网络请求，也不持有 `RunningRepository`。上传只在 ViewModel 结束跑步时触发。

锚点：`feature/running/service/RunTrackingService.kt:49-89`。

### 2.5 模拟提交链路：默认模板优先，失败时退回内置路线

模拟模式的调度入口是 `RunningViewModel.submitSimulation()`：

- 先校验距离 / 时长 / 开始时间 / 速度区间
- 若存在默认模板，则调用 `TrajectoryGenerator.generateFromTemplate(config, template)`
- 若无默认模板，或模板采样结果不足 2 个点，则退回 `TrajectoryGenerator.generate(config)` 的内置校园路线
- 最终都交给 `repository.submitSimulation(config, overrideTrajectory)`，由它先 `startRunning()` 取 `exerciseId`，再构造 `RunningSnapshot` 并复用统一上传链路

锚点：`feature/running/ui/RunningViewModel.kt:214-270` / `feature/running/ui/RunningSimScreen.kt:79-123,126-220,231-231` / `feature/running/data/RunningRepository.kt:230-252` / `feature/running/domain/TrajectoryGenerator.kt:40-65`。

**两条生成分支现在走同一个采样器**。`TrajectoryGenerator` 的内置环线和用户模板都经 `sampleClosedLoop()` → `PolylineSampler` 做段内线性插值，并在末尾补上闭合段。此前内置路线直接取 8 个顶点，相邻点名义步长 40 m、实际几何跳变 100–300 m；现在与模板路径共用同一套步长口径（默认 30 m），且同一 `SimConfig` 两次生成结果一致（`PolylineSampler` 用 `Random(startTimeMillis)` 定种，旧的内置分支用全局 `Random` 做抖动，结果不可复现）。

坐标系改由 `GeneratedTrajectory.isBd09` 随数据一起传递。**2026-10-14 起两条来源都是 `false`**：内置环线的顶点来自 GPX / 谷歌地球（WGS-84），用户模板来自原生 GPS（同样 WGS-84），两者上传前都要过 `wgs84ToBd09`。此前内置环线标 `true`（原样上传）是错的，会让模拟轨迹在校方地图上偏掉整整一步 WGS→BD（本地约 1159 m）——依据见 [`2026-10-13-explore-sports-h5-protocol-evidence`](../compound/2026-10-13-explore-sports-h5-protocol-evidence.md) §1 的实测追加。

模拟与真实跑步共用同一套上传协议，唯一区别是轨迹来源从 GPS 采集换成了生成器。

### 2.6 路线模板系统：录制、存储、生成三层解耦

路线模板系统当前分成三层：

| 层 | 职责 |
|---|---|
| `RouteTemplateViewModel` | 管理录制会话、保存模板、默认模板切换、重命名/删除 |
| `RouteTemplateStore` | 以单 JSON 文件持久化模板，并用 `Mutex` 保证写入串行 |
| `TrajectoryGenerator + PolylineSampler` | 消费默认模板，生成模拟轨迹 |

锚点：`feature/running/ui/RouteTemplateViewModel.kt:33-125` / `feature/running/data/RouteTemplateStore.kt:24-137` / `feature/running/domain/TrajectoryGenerator.kt:40-65` / `feature/running/domain/PolylineSampler.kt:11-105`。

录制路径是：

1. `RouteSimulationSettingsScreen` 作为模板子链入口，负责跳到录制或管理页
2. `RouteTemplateRecordScreen` 调 `viewModel.beginRecording()`，成功后复用 `RunTrackingService.start(context)` 开始采点
3. 结束时先 `RunTrackingService.stop(context)`，再弹保存对话框
4. `saveRecording()` 先走硬闸 `RouteTemplateRules.validate()`，再走警告级 `RouteQualityChecker.evaluate()`，最后由 `RouteTemplateStore.save()` 落到 `route_templates.json`

锚点：`feature/running/ui/RouteSimulationSettingsScreen.kt:51-112` / `feature/running/ui/RouteTemplateRecordScreen.kt:164-229` / `feature/running/ui/RouteTemplateViewModel.kt:64-107` / `feature/running/domain/RouteTemplate.kt:43-52` / `feature/running/domain/RouteQualityChecker.kt:23-56`。

### 2.7 路由上分成两条子链，并各自共享 ViewModel

navigation 层把 running 明确拆成两套共享作用域：

- **主跑步链**：`RUNNING_HOME / ACTIVE / SIM / RESULT` 共用挂在 `Routes.HOME` back stack entry 上的 `RunningViewModel`
- **模板子链**：`RUNNING_ROUTE_SETTINGS / RECORD / LIST / DETAIL` 共用挂在 `RUNNING_ROUTE_SETTINGS` entry 上的 `RouteTemplateViewModel`

锚点：`navigation/Routes.kt:20-29`（主跑步链 4 个常量 + 模板子链 5 个）/ `navigation/NavGraph.kt:262-335`（主跑步链）/ `navigation/NavGraph.kt:359-412`（模板子链）。

这让“主跑步会话状态”和“模板管理状态”既能跨多个页面共享，又不会互相污染。

### 2.8 上传链路：服务端时间、坐标转换、RSA 加密统一收口到 `RunningRepository`

无论真实跑步还是模拟提交，最终上传都走 `RunningRepository.uploadSnapshot()`：

1. `GET /mobile/time/getServerTime.do` 取服务端结束时间
2. 若轨迹点还是 WGS-84，则逐点转 BD-09
3. 组装 `RunningUploadPayload`
4. `RunningEncryption.encryptUploadPayload()` 对**字段名和值**都做 `publicKey2` RSA 加密
5. 作为表单字段 `list` 调 `uploadRunningRecord()`
6. 检查上传响应：只有 `result=OK` 且 `id` 是有效非 0 值才返回成功，否则向 UI 报错，避免“显示上传成功但服务端未入账”

锚点：`feature/running/data/RunningRepository.kt:253-315,442-443` / `feature/running/data/RunningEncryption.kt:11-59` / `core/auth/RSAUtils.kt:23-36` / `feature/running/data/RunningApi.kt:38-55`。

本地抓包文档对这条链路的协议约束有补充：结束上传走 `addExtraCheckNew.do`，请求头必须带 `studentCode`，且 `runningType` 当前在原实现里固定为 `"1"`。来源：`docs/接口分析/跑步接口深度分析报告.md` §8.2 / §8.10。

## 3. 数据与状态

### 3.1 关键类型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `RunningDashboard` | 跑步首页统计摘要 | `feature/running/domain/RunningModels.kt:3-14` |
| `ClubSummary` | 大一大二俱乐部摘要卡片数据 | `feature/running/domain/RunningModels.kt:17-22` |
| `RunningStartInfo` | 一次服务端跑步会话的起始元数据（`exerciseId/memberId` 等） | `feature/running/domain/RunningModels.kt:24-31` |
| `TrackPoint` | 统一轨迹点模型 | `feature/running/domain/RunningModels.kt:33-37` |
| `RunningTrackerState` | 当前采集中的内存态，含 `endTimeMillis` 轨迹锁定位 | `feature/running/domain/RunningModels.kt:39-50` |
| `RunningSnapshot` | 上传前的统一中间态 | `feature/running/domain/RunningModels.kt:52-59` |
| `GeneratedTrajectory` | 模拟轨迹 + 它的坐标系标记 | `feature/running/domain/RunningModels.kt:66-75` |
| `RouteTemplate` | 本地可复用模板资产 | `feature/running/domain/RouteTemplate.kt:3-18` |
| `GeoDistance` | 统一 Haversine 米制距离计算 | `feature/running/domain/GeoDistance.kt:9-18` |
| `ClubTask / ClubCheckRecord` | 俱乐部任务时间线与历史打卡记录 | `feature/running/exercise/domain/ClubModels.kt` |
| `ExerciseQrPayload` | 锻炼考勤二维码 JSON 载荷 | `feature/running/exercise/domain/ExerciseQrPayload.kt` |

### 3.2 状态归属

- **会话中的动态状态**：由 `RunningTracker` 持有在内存 `StateFlow`
- **模板资产状态**：由 `RouteTemplateStore.templates` 持有，并持久化到 `context.filesDir/route_templates.json`
- **页面编排状态**：由 `RunningViewModel` 与 `RouteTemplateViewModel` 分别持有

锚点：`feature/running/service/RunningTracker.kt:21-22` / `feature/running/data/RouteTemplateStore.kt:32-41,114-137` / `feature/running/ui/RunningViewModel.kt:59-67` / `feature/running/ui/RouteTemplateViewModel.kt:38-57`。

### 3.3 持久化边界

- `RunningTrackerState` 不持久化，进程结束即丢
- `RouteTemplateStore` 持久化模板元数据和点位
- 上传链路只持久化到服务端，不在本地保留“历史跑步记录”数据库

当前代码里，模板是唯一会落本地磁盘的 running 资产。

## 4. 关键决策

- **跑步不引入地图 SDK** —— 来源 `codestable/architecture/DESIGN.md` §4；真实跑步与模板录制都以数据仪表盘 + 原生 `LocationManager` 为主，坐标转换走本地数学变换。关联代码：`core/location/LocationProvider.kt:18-69`、`feature/running/service/RunTrackingService.kt:49-65`。
- **路线模板用 JSON 文件而非 Room** —— 来源 `codestable/architecture/DESIGN.md` §4；当前实现为 `RouteTemplateStore + Mutex + route_templates.json`。关联代码：`feature/running/data/RouteTemplateStore.kt:24-137`。

## 5. 代码锚点

- `feature/running/ui/RunningViewModel.kt:214-270` — 模拟提交调度
- `feature/running/ui/RunningViewModel.kt:290-322` — 上传成功/失败与会话保留
- `feature/running/data/RunningRepository.kt:34-224` — 首页统计、开始跑步、年级分流
- `feature/running/data/RunningRepository.kt:230-323` — 模拟提交与统一上传链路
- `feature/running/service/RunningTracker.kt:20-118` — 共享采集状态机与轨迹锁定
- `feature/running/service/RunTrackingService.kt:34-89` — 前台 Service 与 GPS 采集
- `feature/running/domain/GeoDistance.kt:9-18` — 统一距离计算
- `feature/running/data/RunningEncryption.kt:27-59` — 跑步上传 RSA 加密
- `feature/running/domain/TrajectoryGenerator.kt:40-65` — 内置路线 / 模板路线双生成入口，共用闭环采样
- `feature/running/domain/PolylineSampler.kt:22-59` — polyline 插值采样
- `feature/running/domain/RunningCampus.kt:5-16` — 内置环线中心点与坐标系假设说明
- `feature/running/ui/RouteTemplateViewModel.kt:64-107` — 模板录制保存与质量校验接入点
- `feature/running/data/RouteTemplateStore.kt:24-137` — 模板持久化边界
- `feature/running/ui/RunningHomeScreen.kt:89-108` — 真实跑步入口与互斥守卫
- `navigation/NavGraph.kt:262-335`（主跑步链）与 `navigation/NavGraph.kt:359-412`（模板子链）— 两套共享作用域

## 6. 已知约束 / 边界情况

- **模拟与真实上传共用同一服务端协议** —— 不要为模拟模式另造上传接口；统一走 `RunningSnapshot -> uploadSnapshot()`。
- **模板录制和真实跑步不能并发，且守卫是双向的** —— `RouteTemplateViewModel.beginRecording()` 拒绝在跑步会话中录制；反向由 `RunningUiState.isRunSessionActive` / `isTemplateRecordingActive` 把关（`feature/running/ui/RunningViewModel.kt:50-56` / `feature/running/ui/RunningHomeScreen.kt:89-108`）。判据是 `startInfo` 是否存在，而不是 `isSessionActive` —— 两种会话都会让后者为真。
- **上传失败保留会话** —— `uploadSnapshot()` 只在成功时 `completeSession()`；失败时轨迹和 `exerciseId` 留在 `RunningTracker` 里，回跑步页可重试（`feature/running/ui/RunningViewModel.kt:290-322`）。代价是服务端可能留下一个未完成的跑步会话，重试沿用同一个 `exerciseId`。
- **结束时间锁定在 `stopCollecting()`** —— `RunningTrackerState.endTimeMillis` 一旦写入就不再随重试推移，失败后多等几分钟不会把配速算慢（`feature/running/service/RunningTracker.kt:99-118`）。锁定后 `onLocationUpdate()` 不再接受新点。
- **模板保存有两级校验** —— `RouteTemplateRules` 负责硬拒绝，`RouteQualityChecker` 只给 WARNING，不直接拒绝（`feature/running/domain/RouteTemplate.kt:43-52` / `feature/running/domain/RouteQualityChecker.kt:23-56`）。
- **跑步加密要求字段名和值都加密，但 `runningRoute` 必须明文** —— 键名全加密、值除轨迹外全加密（`feature/running/data/RunningEncryption.kt`）。轨迹是唯一的长字段，几十段 RSA 块拼成的密文服务端解不开，会导致记录被判"运动轨迹异常，无效"。实测：同一份数据加密=无效、明文=有效 → [`decision-running-route-plaintext`](../compound/2026-10-14-decision-running-route-plaintext.md)
- **服务端不做校园围栏校验** —— 轨迹明显不在校园内仍判有效。因此上传路径不做 `RouteQualityChecker`（此前加的检查只产出噪音，已移除）；模板录制仍做质量检查，那是本地体验。
- **坐标系只影响轨迹画在哪，不影响有效性** —— 服务端显示侧是 BD-09（官方 H5 用 `BMap` + `Convertor.translate(1→5)`；2026-10-14 先由一次 535 m 东南偏移反证，再用原始 WGS-84 的 GPX 路径录模板 + 模拟提交，官方 App 里轨迹正常落回校内操场，端到端结案），所以上传前一律 WGS-84 → GCJ-02 → BD-09。`RunningCampus` 中心常量已确认为 **WGS-84**（来源是 GPX / 谷歌地球）。
- **用模拟器验证坐标链路时，喂进去的点必须是原始 WGS-84** —— 若喂已经转过 GCJ-02 的轨迹，我们又会做一遍 WGS→GCJ，地图上正好多出一个约 531 m 的东南偏移，看起来像代码错了、其实是测试数据错了。
- 模拟器可以验证跑步上传，但有两个坑：Android 17 的 AVD 上 `adb emu geo fix` 返回 OK 却不再更新位置，要改用 `cmd location providers add-test-provider gps` + `set-test-provider-enabled gps true` + `set-test-provider-location gps --location <lat>,<lng>`；另外宿主机 `127.0.0.1:7897` 的系统代理会透明接管模拟器出口，把国内服务端的请求变成 HTML 503 / 超时，看起来像服务端挂了或代码有 bug。
- **里程口径与官方 H5 不同** —— H5 是「相邻点 ≤3 m 不计里程但点一律保留」，我们是「<1.5 m 或 >120 m 的段连点一起丢」（`feature/running/service/RunningTracker.kt:66-77`）；且 H5 上传 GPS 瞬时速度，我们上传里程/时长平均速度。若服务端按官方口径校验点密度或速度一致性，这是第一个要回看的地方（来源同上 §4）。
- **内置路线现在也走 `PolylineSampler`** —— 曾经直接取 8 个顶点，导致名义 40 m 步长、实际百米级跳变；改动后两条生成分支共享同一采样口径，且同一 `SimConfig` 结果可复现（`feature/running/domain/TrajectoryGenerator.kt:40-65`）。
- **单 JSON 存储的落盘是"写 tmp + rename 覆盖"** —— 不允许先 `delete` 再 `rename`：rename 失败时会连已有文件一起丢掉（`feature/running/data/RouteTemplateStore.kt:128-133`）。rename 失败只清 tmp，保留旧文件。
- **repository 的 `catch` 一律先重抛 `CancellationException`** —— 否则 ViewModel 离开页面触发的取消会被当成业务失败继续写 UI state（`feature/running/data/RunningRepository.kt`，6 处）。
- **`runningType` 当前按 `"1"` 处理** —— 代码与本地协议文档都按这一假设实现；若后续补抓发现跑团/补跑场景存在分支，这里要回看。来源：`docs/接口分析/跑步接口深度分析报告.md` §8.10。
- **年级只读服务端 result** —— 不按学号、性别或本地规则推断年级；大一大二目标值只取服务端字段，`extraDetailInfo` 只能作为接口失败时的目标兜底来源。
- **大一大二开始跑步不走 startRunning.do** —— 会话标识来自 `extraInfo.do` 的 `extraId` 与 `extraDetailInfo.do` 的 `memberId/mixOnceMile`（`feature/running/data/RunningRepository.kt:212-228`）。
- **`result=OK` 不代表服务端认了这条记录** —— 上传成功后还要回读 `mobile/extra/getExtraCheck.do`（参数 `checkId` = 上传返回的 id），拿 `state` / `remark` 才知道服务端判了有效还是无效、原因是什么。`RunningRepository.getRunRecord()` + `RunRecordVerdict` 负责这条链路，结果页文案由 `RunningViewModel.buildFinalResult()` 拼上"（服务端判定：…；轨迹已存：是/否）"。`runningRoute` 在服务端是加密存储的，所以只能判断有没有存、读不出点数。解析器对 `extraInfo` 在 `data` 下还是在响应顶层都兼容。
- **上传成功要校验有效 ID** —— `result=OK` 但 `id` 为空或 `"0"` 视为失败，提示服务端未生成有效跑步记录（`feature/running/data/RunningRepository.kt:293-311`）。
- **`result=OK` + 有效 id 不等于里程入账** —— 服务端可能建了记录却按规则判无效（实测：当日里程已达 `maxDayMile` 时贡献为 0）。结果页因此显示 `RunningResult.creditedKm`（上传前后 `dashboard.todayKm` 之差，`feature/running/ui/RunningViewModel.kt:130-135`），为 0 时明确提示"未计入"，不再只报"上传成功"。
- **有效速度区间以服务端下发为准** —— `extraDetailInfo.do` 的 `minSpeed` / `maxSpeed` 经 `SportsGradeMapper` 解析进 `RunningDashboard.minSpeedKmh/maxSpeedKmh`，模拟提交校验与 `RunningSimScreen` 配色都读这一对值（`feature/running/ui/RunningViewModel.kt:303-321`）。2026-10-13 实测该账号下发的是 `2`–`15` km/h；服务端未下发时兜底 2–15。此前硬编码的 6–15 是客户端自己猜的，比服务端严。
- **日限额 `maxDayMile` 当前不解析也不提示** —— 客户端不预判"今天再跑也不计里程"，提示依赖学校官方 App。要加预警时从同一份 `extraDetail` 取。
- **拒绝通知权限不再阻断跑步** —— 入口只强制要求定位权限；`POST_NOTIFICATIONS` 被拒仅影响前台服务通知可见性（`feature/running/ui/RunningHomeScreen.kt:73-88`）。

## 7. 相关文档

- 网络层：`codestable/architecture/network-overview.md`
- 架构总入口：`codestable/architecture/DESIGN.md`
- 历史规划：`docs/项目规划/路线模拟实现文档.md`
- 协议与上传约束：`docs/接口分析/跑步接口深度分析报告.md`
- 大一大二上传 ID 为 0 修复：`codestable/issues/2026-05-30-junior-running-upload-id-zero/junior-running-upload-id-zero-fix-note.md`
