---
doc_type: architecture
slug: running-overview
scope: 跑步模块的整体架构，覆盖真实 GPS 跑步、前台 Service 跟踪、模拟提交、路线模板录制与管理、以及跑步数据加密上传链路
summary: running 以 RunningRepository + RunningTracker 为主干，把真实跑步、模拟提交、模板录制三条路径收束到同一套 TrackPoint/RunningSnapshot 模型，再通过 SportsRetrofit 完成加密上传
status: current
last_reviewed: 2026-04-21
tags: [running, gps, service, route-template, rsa, sports]
depends_on: [network-overview]
---

# 跑步模块总览

## 0. 术语

- **真实跑步会话** — 先向服务端 `startRunning.do` 申请 `exerciseId`，再由前台 `RunTrackingService` 持续采集 GPS，结束时上传 `RunningSnapshot`
- **模板录制会话** — 不申请 `exerciseId`，只复用 `RunningTracker + RunTrackingService` 采点，结束后经质量校验存成 `RouteTemplate`
- **路线模板** — 保存在 `route_templates.json` 的本地轨迹资产；可设默认、重命名、删除，并在模拟提交时驱动 `PolylineSampler`
- **上传快照** — `RunningSnapshot`，是“可上传一次跑步”的统一中间态；真实跑步与模拟提交都先归一成它再走加密上传

## 1. 定位与受众

本 doc 覆盖 `feature/running/` 的完整主链，不只解释“怎么发请求”，而是解释这三条路径如何共用同一套状态与上传模型：

- 真实跑步：开始跑步 -> GPS 跟踪 -> 结束上传
- 模拟提交：参数校验 -> 生成轨迹 -> 上传
- 模板系统：录制模板 -> 质量校验 -> 本地保存 -> 驱动模拟

适用读者：

- 要改跑步上传链路、加密逻辑或定位策略的人
- 要继续扩路线模板系统的人
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
- 若无默认模板或模板不可用，则退回 `TrajectoryGenerator.generate(config)` 的内置校园路线
- 最终都交给 `repository.submitSimulation()`，由它先 `startRunning()` 取 `exerciseId`，再构造 `RunningSnapshot` 并复用统一上传链路

锚点：`feature/running/ui/RunningViewModel.kt:207-264,327-370` / `feature/running/ui/RunningSimScreen.kt:79-123,126-220,231-231` / `feature/running/data/RunningRepository.kt:107-135` / `feature/running/domain/TrajectoryGenerator.kt:42-70`。

模拟与真实跑步共用同一套上传协议，唯一区别是轨迹来源从 GPS 采集换成了生成器。

### 2.6 路线模板系统：录制、存储、生成三层解耦

路线模板系统当前分成三层：

| 层 | 职责 |
|---|---|
| `RouteTemplateViewModel` | 管理录制会话、保存模板、默认模板切换、重命名/删除 |
| `RouteTemplateStore` | 以单 JSON 文件持久化模板，并用 `Mutex` 保证写入串行 |
| `TrajectoryGenerator + PolylineSampler` | 消费默认模板，生成模拟轨迹 |

锚点：`feature/running/ui/RouteTemplateViewModel.kt:33-125` / `feature/running/data/RouteTemplateStore.kt:24-137` / `feature/running/domain/TrajectoryGenerator.kt:63-70` / `feature/running/domain/PolylineSampler.kt:11-118`。

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

锚点：`navigation/Routes.kt:18-27` / `navigation/NavGraph.kt:169-292`。

这让“主跑步会话状态”和“模板管理状态”既能跨多个页面共享，又不会互相污染。

### 2.8 上传链路：服务端时间、坐标转换、RSA 加密统一收口到 `RunningRepository`

无论真实跑步还是模拟提交，最终上传都走 `RunningRepository.uploadSnapshot()`：

1. `GET /mobile/time/getServerTime.do` 取服务端结束时间
2. 若轨迹点还是 WGS-84，则逐点转 BD-09
3. 组装 `RunningUploadPayload`
4. `RunningEncryption.encryptUploadPayload()` 对**字段名和值**都做 `publicKey2` RSA 加密
5. 作为表单字段 `list` 调 `uploadRunningRecord()`

锚点：`feature/running/data/RunningRepository.kt:140-202` / `feature/running/data/RunningEncryption.kt:11-59` / `core/auth/RSAUtils.kt:23-36` / `feature/running/data/RunningApi.kt:38-45`。

本地抓包文档对这条链路的协议约束有补充：结束上传走 `addExtraCheckNew.do`，请求头必须带 `studentCode`，且 `runningType` 当前在原实现里固定为 `"1"`。来源：`docs/接口分析/跑步接口深度分析报告.md` §8.2 / §8.10。

## 3. 数据与状态

### 3.1 关键类型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `RunningDashboard` | 跑步首页统计摘要 | `feature/running/domain/RunningModels.kt:3-13` |
| `RunningStartInfo` | 一次服务端跑步会话的起始元数据（`exerciseId/memberId` 等） | `feature/running/domain/RunningModels.kt:15-22` |
| `TrackPoint` | 统一轨迹点模型 | `feature/running/domain/RunningModels.kt:24-28` |
| `RunningTrackerState` | 当前采集中的内存态 | `feature/running/domain/RunningModels.kt:30-40` |
| `RunningSnapshot` | 上传前的统一中间态 | `feature/running/domain/RunningModels.kt:42-49` |
| `RouteTemplate` | 本地可复用模板资产 | `feature/running/domain/RouteTemplate.kt:3-18` |

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

- **跑步不引入地图 SDK** —— 来源 `easysdd/architecture/DESIGN.md` §4；真实跑步与模板录制都以数据仪表盘 + 原生 `LocationManager` 为主，坐标转换走本地数学变换。关联代码：`core/location/LocationProvider.kt:18-69`、`feature/running/service/RunTrackingService.kt:49-65`。
- **路线模板用 JSON 文件而非 Room** —— 来源 `easysdd/architecture/DESIGN.md` §4；当前实现为 `RouteTemplateStore + Mutex + route_templates.json`。关联代码：`feature/running/data/RouteTemplateStore.kt:24-137`。

## 5. 代码锚点

- `feature/running/ui/RunningViewModel.kt` — 真实跑步、模拟提交、结果态编排
- `feature/running/data/RunningRepository.kt:33-202` — 首页统计、开始跑步、统一上传链路
- `feature/running/service/RunningTracker.kt:24-118` — 共享采集状态机
- `feature/running/service/RunTrackingService.kt:34-89` — 前台 Service 与 GPS 采集
- `feature/running/data/RunningEncryption.kt:27-59` — 跑步上传 RSA 加密
- `feature/running/domain/TrajectoryGenerator.kt:42-70` — 内置路线 / 模板路线双生成入口
- `feature/running/domain/PolylineSampler.kt:26-63` — 模板 polyline 采样
- `feature/running/ui/RouteTemplateViewModel.kt:64-107` — 模板录制保存与质量校验接入点
- `feature/running/data/RouteTemplateStore.kt:24-137` — 模板持久化边界
- `navigation/NavGraph.kt:169-292` — 主跑步链 / 模板子链的共享作用域

## 6. 已知约束 / 边界情况

- **模拟与真实上传共用同一服务端协议** —— 不要为模拟模式另造上传接口；统一走 `RunningSnapshot -> uploadSnapshot()`。
- **模板录制和真实跑步不能并发** —— `RouteTemplateViewModel.beginRecording()` 会在已有真实跑步会话时拒绝录制（`feature/running/ui/RouteTemplateViewModel.kt:64-73`）。
- **模板保存有两级校验** —— `RouteTemplateRules` 负责硬拒绝，`RouteQualityChecker` 只给 WARNING，不直接拒绝（`feature/running/domain/RouteTemplate.kt:43-52` / `feature/running/domain/RouteQualityChecker.kt:23-56`）。
- **上传前才做 WGS-84 -> BD-09 转换** —— 真实跑步采集阶段保留原生坐标，统一在 repository 上传前转换（`feature/running/data/RunningRepository.kt:146-157`）。
- **跑步加密要求字段名和值都加密** —— 不能只加密 JSON value（`feature/running/data/RunningEncryption.kt:37-58`）。
- **`runningType` 当前按 `"1"` 处理** —— 代码与本地协议文档都按这一假设实现；若后续补抓发现跑团/补跑场景存在分支，这里要回看。来源：`docs/接口分析/跑步接口深度分析报告.md` §8.10。

## 7. 相关文档

- 网络层：`easysdd/architecture/network-overview.md`
- 架构总入口：`easysdd/architecture/DESIGN.md`
- 历史规划：`docs/项目规划/路线模拟实现文档.md`
- 协议与上传约束：`docs/接口分析/跑步接口深度分析报告.md`
