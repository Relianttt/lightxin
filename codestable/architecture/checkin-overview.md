---
doc_type: architecture
slug: checkin-overview
scope: feature/checkin/ 的查寝签到全流程（任务列表 / 详情 / 拍照 / 定位 / 提交），并作为 fdygl 域同源任务的寄生入口（当前承载节假日离返校登记，未来可扩返校登记等同源任务）
summary: checkin 以分页列表 + 详情两页式交互，详情页集成了原生定位（WGS-84 → BD-09 坐标转换）、照片上传、与多字段鉴权提交；独立 FileUploadApi 处理图片上传。列表页同时双 section 承载同 fdygl 域的节假日任务（feature/holiday/），数据各自独立加载、错误彼此隔离、UI 共用一屏
status: current
last_reviewed: 2026-05-03
tags: [checkin, sign-in, location, photo-upload, pagination, holiday]
depends_on: [network-overview]
---

# 查寝模块总览

## 0. 术语

- **taskDateId** — 签到任务的唯一标识，从列表接口多个可能字段中提取（`taskDateId` / `id` / `userTaskId` / `taskId`），是后续详情和提交的核心参数
- **WGS-84 → BD-09** — 原生定位得到 WGS-84 坐标，经 `CoordinateConverter.wgs84ToBd09()` 转为 BD-09 后上传；服务端期望 BD-09 格式
- **多 header 鉴权** — 查寝接口（`fdygl.aiit.edu.cn`）的 `AuthInterceptor` 需同时携带 `accessToken / access_token / userCode / xh / userType / X-Requested-With / Origin / Referer`，否则返回 `-100 非法访问`
- **照片上传** — 使用独立 `FileUploadApi`（Multipart），上传结果的数据结构不确定（可能是字符串或 JSON 对象），`extractUploadUrl()` 做启发式提取

## 1. 定位与受众

本 doc 描述 `feature/checkin/` 的职责边界：

- 查寝签到任务的列表浏览、详情查看、拍照上传、定位签到完整流程
- 不覆盖其他类型的签到（如 AI 课堂的数字码签到）

适用读者：

- 要改签到流程、加新字段、或调定位策略的人
- 要排查"上传照片后找不到 URL"或"签到提交返回 -100"的人
- 要理解查寝数据如何被首页消费的人

## 2. 结构与交互

### 2.1 两页式：列表页 + 详情页

```
CheckinListScreen    → CheckinViewModel      → CheckinRepository → CheckinApi
CheckinDetailScreen  → CheckinDetailViewModel → CheckinRepository → CheckinApi
                                               → FileUploadApi
                                               → LocationProvider
```

两页各自有独立的 ViewModel：
- `CheckinViewModel` — 管理列表分页加载
- `CheckinDetailViewModel` — 管理详情、定位、拍照、提交

### 2.2 CheckinApi 包含三个接口 + 一个上传接口

锚点：`feature/checkin/data/CheckinApi.kt:13-43`

| 接口 | 方法 | 用途 |
|---|---|---|
| `pageStudentSignIn` | POST | 分页查询签到任务列表 |
| `getTaskInfoByDateId` | POST | 获取任务详情（含签到范围/位置） |
| `signIn` | POST | 提交签到 |
| `FileUploadApi.uploadFile` | POST Multipart | 照片上传 |

所有查寝接口使用 JSON body 格式，认证通过 `AuthInterceptor` 注入（与主站 token 体系共享，但需要额外的 header 字段）。

### 2.3 分页加载

锚点：`feature/checkin/data/CheckinRepository.kt:26-55`

`CheckinRepository.getTasks()` 使用 `pageNum` / `pageSize` 分页，固定 `type=1`、`taskMajorType=3`。

`CheckinViewModel` 管理分页状态：
- 首次加载 `loadInitial()` → page 1
- `loadMore()` → 追加下一页
- `hasMore` 根据返回列表大小是否 ≥ pageSize（10）判断
- `isLoadingMore` 防重复加载

列表数据映射时，`taskDateId` 从多个可能字段中取第一个非空值（`row.taskDateId ?: row.id ?: row.userTaskId ?: row.taskId`），因为后端在不同场景下返回的字段名不一致。

### 2.4 详情页集成定位 + 拍照 + 提交

锚点：`feature/checkin/ui/CheckinDetailViewModel.kt:42-164`

详情页的执行流程：

1. **加载详情** — `getTaskDetail(taskDateId)` 获取签到位置范围、是否需要拍照等信息
2. **获取定位** — 先尝试 `getLastKnownLocation()` 快速定位，失败则订阅 `locationUpdates()` 取首次结果
3. **坐标转换** — `CoordinateConverter.wgs84ToBd09()` 将原生 WGS-84 转为 BD-09
4. **拍照（可选）** — 根据 `detail.needPhoto` 决定是否需要拍照
5. **提交签到** — 先上传照片（如需）→ 提交 `taskDateId + photoUrl + place + latLng + outSignin`

定位的两种策略：
- 快速路径：`locationProvider.getLastKnownLocation()`（可能为 null）
- 持续路径：`locationProvider.locationUpdates(2000ms, 0m)` 取第一个有效值后取消订阅

### 2.5 照片上传的启发式 URL 提取

锚点：`feature/checkin/data/CheckinRepository.kt:156-180`

`FileUploadApi.uploadFile()` 返回的 `data` 字段类型为 `JsonElement?`（可能是字符串或 JSON 对象），`extractUploadUrl()` 按优先级尝试：
1. JSON 基本类型 → 直接作为 URL
2. JSON 对象 → 搜索 `url` / `fileUrl` / `fullPath` / `path` / `filePath` 等常见键
3. 遍历所有字段值，查找以 `http://` 或 `https://` 开头或包含 `/group` 的字符串

这是对后端不一致响应结构的防御性处理。

### 2.6 列表页同时承载 fdygl 域同源任务（节假日 / 未来返校登记）

`CheckinListScreen` 不是纯查寝列表，而是 fdygl 域（`fdygl.aiit.edu.cn`）同源任务的统一入口：

- 查寝任务（`tasks: List<CheckinTask>`）— 主流程，§2.1–2.5 覆盖
- 节假日离返校登记任务（`holidayTasks: List<HolidayTask>` + `holidayError: String?`）— 寄生 section，数据来自 `feature/holiday/data/HolidayRepository`

两组数据由 `CheckinViewModel` 在 `init` 中并发触发（`loadInitial()` + `loadHolidays()`）但状态彼此隔离——查寝主流程的 `error` / `isLoading` 不被节假日加载影响；节假日加载失败只在 section 顶部显示错误行，可独立重试（`retryHoliday()`）不触发主流程刷新。`CheckinListScreen` 用两个 LazyColumn section 渲染，分别走 `TaskCard` / `HolidayTaskCard`，section header 区分。

节假日除"列表页入口"外完全独立——独立 Api / Repository / 路由 / ViewModel / 表单页。复用查寝模块的只有：

- `@CheckinRetrofit`（同 fdygl 域，没必要新建实例）
- `AuthInterceptor` 的 fdygl 档（多 header 鉴权对节假日同样有效）
- 列表页本身（作为入口寄生）

`HolidayApi` 由 `HolidayModule` 通过 `@CheckinRetrofit` 提供，`HolidayRepository` 直接注入 API，不在构造函数内自行 `retrofit.create()`。这样节假日和查寝同源接口共用同一个 fdygl 网络栈，同时保持 Repository 只依赖业务 API。

这种"主任务 + 同域寄生 section"模式有意为后续同 fdygl 域扩展（如返校登记、其他学工在线任务）预留——新增同类任务时沿用 `holidayTasks` / `holidayError` / `retryHoliday` 这套字段命名与并发加载风格，不必单独建一级导航或独立 tab。

锚点：`feature/checkin/ui/CheckinViewModel.kt:14-26,38-42,66-87,113-115` / `feature/checkin/ui/CheckinListScreen.kt:140-157` / `feature/holiday/data/HolidayRepository.kt:22-25,204-209` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md`。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `CheckinTask` | id / taskName / taskDateId / isSigned / startTime / endTime | `CheckinModels.kt:6-13` |
| `TaskDetail` | taskName / taskDateId / needPhoto / isSigned / signinPlace / locationRange / centerLng / centerLat / address | `CheckinModels.kt:18-30` |
| `CheckinUiState` | tasks / holidayTasks / isLoading / isLoadingHoliday / isLoadingMore / error / holidayError / hasMore / currentPage | `CheckinViewModel.kt:16-26` |
| `CheckinDetailUiState` | detail / bdLng/bdLat / locationStatus / photoUri / isSubmitting / submitSuccess / submitError | `CheckinDetailViewModel.kt:19-36` |

### 3.2 首页对查寝数据的消费

`feature/home/` 通过 `CheckinRepository` 获取待签到任务列表：
- `HomeBootstrap` 在冷启动时预取查寝数据
- `SceneResolver` 在 22:00 后未签到时可触发 `EveningCheckin` 场景
- `HomeDashboard` 在开始时间前后 4 小时窗口内显示查寝卡片

## 4. 关键决策

- **taskDateId 从多字段提取** —— 后端不同接口返回的 ID 字段名不一致，Repository 层做兼容。来源：`CheckinRepository.kt:45`。
- **定位策略：先取缓存再订阅** —— `getLastKnownLocation()` 快速返回，避免每次冷启动都等待 GPS 锁定。来源：`CheckinDetailViewModel.kt:82-101`。
- **坐标转换在客户端完成** —— 原生定位得到 WGS-84，上传前转为 BD-09，不依赖地图 SDK。来源：`CheckinDetailViewModel.kt:103-111`、`core/location/CoordinateConverter.kt`。
- **多 header 鉴权** —— 查寝接口需要 accessToken / access_token / userCode / xh / userType / X-Requested-With / Origin / Referer 共 8 个字段同时携带。来源：`compound/2026-04-22-decision-checkin-multi-header-auth.md`。
- **照片上传结果启发式解析** —— 后端返回结构不固定，不做严格类型匹配。来源：`CheckinRepository.kt:156-180`。
- **查寝列表页作为 fdygl 域同源任务的寄生入口** —— 节假日不另开一级导航或独立 tab，CheckinListScreen 双 section 同时渲染查寝与节假日；两套数据加载、错误、重试各自独立。同模式预留给未来同源任务（如返校登记）。来源：`feature/checkin/ui/CheckinViewModel.kt:38-42` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md` §1。

## 5. 代码锚点

- `feature/checkin/data/CheckinApi.kt:13-43` — 查寝三个接口 + 上传接口
- `feature/checkin/data/CheckinRepository.kt:26-55` — 分页加载
- `feature/checkin/data/CheckinRepository.kt:57-95` — 详情获取
- `feature/checkin/data/CheckinRepository.kt:97-180` — 照片上传 + 启发式 URL 提取
- `feature/checkin/data/CheckinRepository.kt:114-138` — 签到提交
- `feature/checkin/domain/CheckinModels.kt:6-30` — CheckinTask / TaskDetail
- `feature/checkin/ui/CheckinViewModel.kt:24-92` — 列表分页状态管理
- `feature/checkin/ui/CheckinDetailViewModel.kt:42-164` — 详情 + 定位 + 提交
- `core/location/CoordinateConverter.kt` — WGS-84 → BD-09 转换

## 6. 已知约束 / 边界情况

- **查寝接口使用 JSON body，而非 form-urlencoded** —— 与其他模块（如 login、schedule）不同。来源：`CheckinApi.kt:17-32`。
- **outSignin 固定为 "0"** —— 不支持"校外签到"场景，超出范围无法提交。来源：`CheckinRepository.kt:126`。
- **定位超时处理不明确** —— `locationUpdates()` 没有超时机制，若无法定位会持续阻塞。当前依赖用户在 UI 层的交互触发。
- **照片上传后没有预览确认** —— 选择照片后直接上传，没有让用户确认的中间步骤。
- **分页使用 `type=1`、`taskMajorType=3` 硬编码** —— 若后端调整类型值需要同步修改。
- **fdygl 域同源任务寄生时不跟查寝主流程互相影响** —— 节假日（以及未来同模式扩展）的加载、错误、重试各自走独立字段（`holidayTasks` / `holidayError` / `retryHoliday`）。新增同源任务时沿用这套命名，不要塞进查寝主 `error` / `tasks` 通道，否则一处失败会污染另一处的可用性。

## 7. 相关文档

- 网络层多域名 + 多 header 鉴权：`codestable/architecture/network-overview.md`
- 查寝多 header 鉴权决策：`codestable/compound/2026-04-22-decision-checkin-multi-header-auth.md`
- 首页消费查寝数据：`codestable/architecture/home-overview.md` §2.2 / §2.4 / §4.4
- 节假日离返校登记搭载在查寝列表页：`CheckinViewModel` 同时持有 `tasks / holidayTasks / holidayError`，`CheckinListScreen` 双 section 渲染。详见 `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md`
- 架构总入口：`codestable/architecture/DESIGN.md`
