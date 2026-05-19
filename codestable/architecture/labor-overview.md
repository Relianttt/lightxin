---
doc_type: architecture
slug: labor-overview
scope: feature/labor/ 的劳动教育模块：工时总览、活动记录分页、活动详情（只读）
summary: labor 以 LaborRepository 封装三个只读查询接口，ViewModel 并行加载工时总览与活动列表，活动详情通过 ModalBottomSheet 在列表页内展示；模块整体只读，无提交逻辑
status: current
last_reviewed: 2026-05-19
tags: [labor, hours, activity, readonly, pagination, bottom-sheet]
depends_on: [network-overview]
---

# 劳动教育模块总览

## 0. 术语

- **工时总览** — `HoursSummary`，包含志愿/暑期/劳动/社区/其他五类工时及总和；以 `Double` 精度存储
- **活动记录** — `ActivityRecord`，劳动教育活动的列表项，含项目类型、活动名称、工时、日期
- **活动详情** — `ActivityDetail`，单个活动的完整信息（只读），含主办方、类型、级别等

## 1. 定位与受众

本 doc 描述 `feature/labor/` 的职责边界：

- 劳动教育工时的只读查看，无提交、修改、删除功能
- 工时总览 + 活动记录分页 + 活动详情三层展示

适用读者：

- 要改工时展示方式、加新字段、或调分页逻辑的人
- 要理解 labor 数据如何被首页消费的人
- 要排查"工时数据加载失败"或"活动列表重复"的人

## 2. 结构与交互

### 2.1 两页式：总览列表页 + Bottom Sheet 详情

```
LaborSummaryScreen     → LaborViewModel     → LaborRepository → LaborApi
```

活动详情通过 `ModalBottomSheet` 在 `LaborSummaryScreen` 内展示，不跳转独立页面。`LaborViewModel` 统一管理工时总览、活动分页列表和详情 Bottom Sheet 状态：
- 点击活动卡片 → `onRecordClick(record)` → 触发详情请求 + 展开 Bottom Sheet
- 关闭 Bottom Sheet → `dismissDetail()`

`LaborDetailScreen` / `LaborDetailViewModel` 文件已删除。

### 2.2 LaborApi 三个只读接口

锚点：`feature/labor/data/LaborApi.kt:7-36`

| 接口 | 方法 | 用途 |
|---|---|---|
| `queryPersonalTimesTotal` | POST form | 查询个人工时总览（五类工时） |
| `queryTimesDetailsPage` | POST form | 分页查询活动记录 |
| `queryTimesDetails` | POST form | 查询单个活动详情 |

所有接口使用 form-urlencoded，认证参数（`userCode / xh / accessToken`）由 `LaborRepository.authParams()` 从 `TokenManager` 统一获取。xh 与 userCode 相同。

### 2.3 并行加载总览与列表

锚点：`feature/labor/ui/LaborViewModel.kt:37-70`

`LaborViewModel.loadInitial()` 并行执行两个请求：
- `repository.getHoursSummary()`
- `repository.getActivities(page = 1)`

两者任一失败都会置 `error` 状态。当前实现中，hours 失败会提前 return，activities 失败继续执行。这是两个请求的错误处理策略不一致，需要注意。

### 2.4 分页加载与去重

锚点：`feature/labor/ui/LaborViewModel.kt:73-98`

`loadMore()` 按 page 追加数据，并通过 `id` 去重：
```kotlin
val existingIds = it.activities.mapTo(mutableSetOf()) { r -> r.id }
val newRecords = list.filter { r -> r.id !in existingIds }
```

`hasMore` 判断条件为 `newRecords.isNotEmpty() && list.size >= 10`——这是比 `checkin` 更严格的条件（checkin 只判断 `list.size >= 10`），避免分页加载到空数据后仍继续请求。

### 2.5 首页对 labor 数据的消费

锚点：`feature/home/ui/HomeViewModel.kt:140-161`

首页 `HomeViewModel` 在 splash 预加载阶段之后，额外并发加载 labor 摘要数据。但 `HomeDashboard` 当前没有渲染劳动教育卡片（DESIGN.md §7 记录为已知约束）。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `HoursSummary` | voluntaryTimes / summerTimes / laborTimes / socialTimes / otherTimes + totalTimes | `LaborModels.kt:3-12` |
| `ActivityRecord` | id / projectTypeName / type / activityName / serviceTimes / createDate | `LaborModels.kt:14-21` |
| `ActivityDetail` | activityName / activityType / activityLevel / organizer / serviceTimes / createDate | `LaborModels.kt:23-30` |
| `LaborUiState` | hoursSummary / activities / isLoading / isLoadingMore / error / hasMore / currentPage / showDetailSheet / selectedRecord / selectedDetail / isDetailLoading / detailError | `LaborViewModel.kt:15-27` |

### 3.2 HoursSummary 的计算属性

`totalTimes` 是 `val` 计算属性，对五类工时求和。使用时注意这是一个无缓存的计算，每次访问都会重新求和。

## 4. 关键决策

- **模块只读** —— labor 只展示数据，没有提交/编辑/删除功能。这与 checkin 的交互式签到形成对比。
- **并行加载而非串行** —— 总览和列表同时请求，减少用户等待时间。来源：`LaborViewModel.kt:42-43`。
- **分页去重** —— 用 `id` 集合防止重复项加入列表。来源：`LaborViewModel.kt:84`。
- **认证参数统一由 Repository 注入** —— `authParams()` 从 `TokenManager` 读取 userCode 和 accessToken，外部不感知认证细节。来源：`LaborRepository.kt:23-26`。

## 5. 代码锚点

- `feature/labor/data/LaborApi.kt:7-36` — 三个只读接口
- `feature/labor/data/LaborRepository.kt:19-111` — 认证 + 查询 + 错误映射
- `feature/labor/data/LaborResponse.kt:6-59` — 三层嵌套的响应模型
- `feature/labor/domain/LaborModels.kt:3-30` — HoursSummary / ActivityRecord / ActivityDetail
- `feature/labor/ui/LaborViewModel.kt` — 并行加载 + 分页 + 去重 + Bottom Sheet 状态
- `feature/labor/ui/LaborSummaryScreen.kt` — 工时总览 + 活动列表 + 详情 Bottom Sheet UI

## 6. 已知约束 / 边界情况

- **HoursSummary 的五类工时字段全部为 `Double?` → `0.0` 兜底** —— 后端可能返回 null，此时计为 0。来源：`LaborRepository.kt:38-45`。
- **hours 失败比 activities 失败的优先级更高** —— `loadInitial()` 中 hours 失败会提前 return，activities 失败则继续。来源：`LaborViewModel.kt:45-53` vs `54-69`。
- **NestedData<T> 响应包装** —— 后端工时接口有两层 `data` 嵌套（`response.data.data`）。来源：`LaborResponse.kt:6-14`、`LaborRepository.kt:35`。
- **首页 labor 数据被加载但不渲染** —— `HomeViewModel` 加载 labor 数据但 `HomeDashboard` 没有对应卡片。来源：`feature/home/ui/HomeViewModel.kt:140-161`。
- **分页去重基于 id 字符串** —— 假设 id 全局唯一。如果后端在跨页时返回相同 id 的不同内容，仍会被去重丢弃。

## 7. 相关文档

- 网络层：`codestable/architecture/network-overview.md`
- 首页消费 labor 数据：`codestable/architecture/home-overview.md` §3.3
- 架构总入口：`codestable/architecture/DESIGN.md`
