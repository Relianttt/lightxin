---
doc_type: architecture
slug: checkin-overview
scope: feature/checkin/ 的查寝签到全流程：任务列表、详情获取、照片上传、定位转换、签到提交
summary: checkin 以分页列表 + 详情两页式交互，详情页集成了原生定位（WGS-84 → BD-09 坐标转换）、照片上传、与多字段鉴权提交；独立 FileUploadApi 处理图片上传
status: current
last_reviewed: 2026-04-22
tags: [checkin, sign-in, location, photo-upload, pagination]
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

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `CheckinTask` | id / taskName / taskDateId / isSigned / startTime / endTime | `CheckinModels.kt:6-13` |
| `TaskDetail` | taskName / taskDateId / needPhoto / isSigned / signinPlace / locationRange / centerLng / centerLat / address | `CheckinModels.kt:18-30` |
| `CheckinUiState` | tasks / isLoading / isLoadingMore / error / hasMore / currentPage | `CheckinViewModel.kt:14-22` |
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

## 7. 相关文档

- 网络层多域名 + 多 header 鉴权：`codestable/architecture/network-overview.md`
- 查寝多 header 鉴权决策：`codestable/compound/2026-04-22-decision-checkin-multi-header-auth.md`
- 首页消费查寝数据：`codestable/architecture/home-overview.md` §2.2 / §2.4 / §4.4
- 架构总入口：`codestable/architecture/DESIGN.md`
