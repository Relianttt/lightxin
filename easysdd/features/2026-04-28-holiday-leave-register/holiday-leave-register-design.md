---
doc_type: feature-design
feature: 2026-04-28-holiday-leave-register
status: implemented
summary: 在查寝签到模块基础上新增"节假日离返校登记"功能——首页展示节假日登记卡片，查寝列表页集成节假日 section，点击进入独立表单页填写离返校信息并提交。日期选择器采用自定义两步 BottomSheet（日历选日→时分确认），字段按节假日配置 Enable 标记条件显隐。
tags: [holiday, checkin, home, register, leave, date-picker]
---

## 0. 术语约定

| 术语 | 定义 | 防冲突结论 |
|------|------|-----------|
| `HolidayTask` | 节假日登记任务领域模型，含 holidayId/name/登记窗口/是否已登记等 | grep 全项目无冲突 |
| `HolidayFormData` | 登记表单数据，含 stroke(0=离校/1=留校)/reason/destination/urgentPhone/日期范围 | grep 无冲突 |
| `TaskListItem` | sealed interface，统一查寝 CheckinTask 和 HolidayTask 为列表项 | grep 无冲突 |
| `StrokeOption` | 离校/留校字典选项，label + value | grep 无冲突 |
| `HolidayCard` | 首页节假日卡片 Composable（放在 HomeDashboard.kt 内，private） | grep 无冲突 |
| `HolidayRegisterScreen` | 登记表单独立页面 | grep 无冲突 |
| `HolidayRepository` | 节假日数据仓库，封装 5 个接口调用 | grep 无冲突 |
| `HolidayApi` | Retrofit 接口定义 | grep 无冲突 |
| `fdygl` | 辅导员管理域名 `fdygl.aiit.edu.cn` 的缩写，节假日与查寝共享该域 | 已有术语，复用 |

## 1. 决策与约束

### 需求摘要

**做什么**：学生在 App 首页看到节假日登记卡片（当前时间在 `registerStartDate ~ registerEndDate` 窗口内时显示），点击进入查寝列表页（集成节假日 section），选择节假日任务后进入独立表单页填写离返校信息并提交。

**为谁做**：在校学生（role=STUDENT），需要完成节假日离校/留校登记。

**成功标准**：
- 首页在登记窗口内显示节假日卡片，窗口外不显示
- 查寝列表页同时展示查寝任务和节假日任务
- 登记表单页支持新建和回填已有记录
- 提交成功后返回列表页并触发刷新

**明确不做什么**：
- 不加入 HomeBootstrap 冷启动预加载（首页按需加载）
- 节假日列表不做分页（数据量小）
- 不做审批流程（`isApproval` 字段保留但不实现审批 UI）
- 不做附件上传（`XXXProofMaterialsEnable` 字段保留但不实现）
- 不处理取消/撤回登记（接口未抓取到）
- 不在本次 PR 中把 `role`/`roleId` 加入 AuthInterceptor（联调后根据实际需要再补）

### 关键决策

| 决策 | 选择 | 被拒方案 |
|------|------|---------|
| 节假日功能放哪 | 新建 `feature/holiday/` 模块，平行于 `feature/checkin/` | 放入 checkin 模块 → 职责混淆，后续维护难 |
| Retrofit 实例 | 复用 `@CheckinRetrofit`（同 fdygl 域） | 新建 HolidayRetrofit → 重复配置，无收益 |
| 列表页整合方式 | 在 CheckinListScreen 内双 section 展示 | 独立节假日列表页 → 增加导航层级，用户多一次点击 |
| 登记表单 | 独立页面 `/holiday/register/{holidayId}` | 在列表页内弹窗 → 表单字段多，弹窗体验差 |
| 首页卡片显示逻辑 | 解析 `registerStartDate/registerEndDate`，当前时间在窗口内 | 用服务器 status 字段 → 无法精确控制前端展示时机 |
| 冷启动预加载 | 不入 HomeBootstrap，在 HomeViewModel.loadExtras() 中按需加载 | 入 HomeBootstrap → 节假日非关键路径，不值得阻塞启动 |
| 日期时间选择器 | 自定义两步 BottomSheet：先选日历日期 → 收起 → 再选时分 + 确认 | Material3 DatePicker → 蓝紫色调与项目陶土色系割裂；原应用滚轮选择器 → 交互过重 |
| 选择器背景 | 纯色底（LxSand），1px 陶土横线分隔，不用高斯模糊 | 模糊玻璃 → 不符合 Anthropic 编辑式克制风格 |

### 主流程概述

**正常路径**：
1. 首页加载 → `getRegistrationPage(pageNum=1)` 取第一个未登记节假日 → 窗口内显示 HolidayCard
2. 点击 HolidayCard → 导航到查寝列表页（CHECKIN_LIST）
3. 列表页并发加载查寝任务 + 节假日任务 → 双 section 渲染
4. 点击节假日项 → 导航到登记表单页（HOLIDAY_REGISTER/{holidayId}）
5. 表单页并发加载节假日配置 + 已有记录(回填) + 字典选项
6. 用户填写 → 提交 → 成功 → 返回列表 + 刷新信号

**关键异常/边界**：
- 无节假日或全部已登记 → 首页不显示卡片
- 登记窗口外 → 不显示卡片；后端也会校验
- `getHolidayRegister` 返回 null → 表单初始化为空，允许新建
- 网络错误 → 复用 `mapError()` 模式显示中文错误提示
- `role`/`roleId` 缺失导致 -100 → 先不带这两个头联调，需要时补 AuthInterceptor

## 2. 接口契约

### 2.1 后端 API

所有接口均走 `fdygl.aiit.edu.cn`，复用 `@CheckinRetrofit`。

**获取登记列表** — `POST /app/holiday/getRegistrationPage`
```
请求: { "studentId": "3233032235", "pageNum": 1 }
响应: { "code": "0", "rows": [{ "id": "...", "name": "2026年五一离返校", "registerStartDate": "2026-04-30 00:00", "registerEndDate": "2026-05-05 23:59", "allowStaySchool": "1", "startDate": "...", "endDate": "..." }], "total": 1 }
错误: { "code": "-100", "msg": "非法访问" }
```
// 来源：docs/接口分析/节假日离返校登记接口分析.md §2.1

**获取节假日详情** — `GET /app/holiday/getHolidaySetById?holidayId={id}`
```
响应: { "code": "0", "data": { "id": "...", "name": "...", "allowStaySchool": "1", "registerStartDate": "...", "registerEndDate": "...", ... } }
```
// 来源：同上 §2.2

**获取字典** — `POST /app/dict/list`
```
请求: { "dictType": "app_stroke_type" }
响应: { "code": "0", "data": [{ "label": "离校", "value": "0" }, { "label": "留校", "value": "1" }] }
```
// 来源：同上 §2.3

**获取已有登记** — `GET /app/holiday/getHolidayRegister?holidayId={id}&studentId={sid}`
```
响应(无记录): { "code": "0", "data": null }
响应(有记录): 联调后确认 data 结构
```
// 来源：同上 §2.4

**提交登记** — `POST /app/holiday/save`
```
请求: { "id": "", "holidayId": "...", "studentId": "...", "list": [{ "startDate": "2026-04-30 17:16", "endDate": "2026-05-05 17:16", "stroke": "0", "reason": "回家", "destination": "", "urgentPhone": "", "enableAttachmentList": [], "requireAttachmentList": [] }] }
响应: { "code": "0", "msg": "操作成功" }
```
// 来源：同上 §2.5

### 2.2 前端组件契约

**HolidayCard**（新增，HomeDashboard.kt 内 private）
```
Props: task: HolidayTask, onClick: () -> Unit
渲染: 复用 DashboardCard + PulseDot 模式，标题"节假日登记"，时间范围用 registerStartDate~registerEndDate
```
// 来源：参照 HomeDashboard.kt:444 CheckinCard

**CheckinListScreen**（修改）
```
变更前 Props: onBack, onTaskClick: (taskDateId) -> Unit, shouldRefresh, onRefreshConsumed
变更后 Props: 新增 onHolidayClick: (holidayId: String) -> Unit
列表模型: 从 List<CheckinTask> 改为 List<TaskListItem>（sealed interface）
Section 区分: TaskListItem.Checkin → TaskCard(查寝样式)，TaskListItem.Holiday → HolidayTaskCard(节假日样式)
```
// 来源：CheckinListScreen.kt:45-52

**HolidayRegisterScreen**（新增）
```
Props: holidayId: String, onSubmitSuccess: () -> Unit, onBack: () -> Unit
状态来源: HolidayRegisterViewModel (Hilt)
表单字段: 日期范围选择、离校/留校 radio、事由/目的地/紧急电话文本输入
提交按钮 → submit() → 成功 → onSubmitSuccess()
```
// 来源：独立页面，无参照组件

### 2.3 状态归属

| 状态 | 归属 |
|------|------|
| `HomeDashboardData.holidayTask` | HomeViewModel._uiState |
| `CheckinUiState.holidayTasks` | CheckinViewModel._uiState |
| `HolidayRegisterUiState` | HolidayRegisterViewModel._uiState |
| `checkin_refresh` (savedStateHandle) | NavGraph CHECKIN_LIST backStackEntry |

## 3. 实现提示

### 目标文件状况评估

全部健康，可直接追加：
- `HomeDashboard.kt` (637行) — 新增 HolidayCard 是已有卡片体系的延伸
- `HomeViewModel.kt` (272行) — 新增 loadHoliday() 是 loadExtras() 的自然扩展
- `CheckinListScreen.kt` (204行) — 引入 sealed interface 统一列表项，职责不膨胀
- `CheckinViewModel.kt` (92行) — 新增节假日加载，轻量追加
- `AuthInterceptor.kt` (109行) — 可能需要追加 role/roleId，联调后确定
- `Routes.kt` / `NavGraph.kt` — 各新增一个路由条目

### 改动计划

**新建文件 (7)**:
1. `feature/holiday/data/HolidayApi.kt` — Retrofit 接口（5 个方法，复用 @CheckinRetrofit）
2. `feature/holiday/data/HolidayResponse.kt` — 响应 DTO（6 个 data class）
3. `feature/holiday/data/HolidayRepository.kt` — 数据仓库 + Hilt DI Module
4. `feature/holiday/domain/HolidayModels.kt` — 领域模型（HolidayTask, HolidayFormData, StrokeOption, TaskListItem sealed interface）
5. `feature/holiday/ui/HolidayRegisterScreen.kt` — 登记表单页 Composable
6. `feature/holiday/ui/HolidayRegisterViewModel.kt` — 表单 ViewModel
7. （HolidayCard 放 HomeDashboard.kt 内，不单独建文件）

**修改文件 (6)**:
1. `feature/home/ui/HomeDashboard.kt` — 新增 HolidayCard + shouldShowHoliday()
2. `feature/home/ui/HomeViewModel.kt` — 新增 loadHoliday() + HomeDashboardData.holidayTask
3. `feature/checkin/ui/CheckinListScreen.kt` — 双 section 列表 + onHolidayClick 回调
4. `feature/checkin/ui/CheckinViewModel.kt` — 新增 loadHolidays() + holidayTasks 状态
5. `navigation/Routes.kt` — 新增 HOLIDAY_REGISTER 路由常量
6. `navigation/NavGraph.kt` — 新增 holiday composable + 刷新信号处理

### 实现风险与约束

| 风险 | 缓解 |
|------|------|
| `role`/`roleId` 头缺失导致 -100 | 先不带这两个头联调；返回 -100 则在 AuthInterceptor fdygl 档追加 |
| `getHolidayRegister` 有记录时的 data 结构未知 | 先按 `HolidayFormData?` 建模，联调后修正 |
| 表单日期选择在 Android 上的组件选择 | 使用 Material3 DatePickerDialog，格式化为 `yyyy-MM-dd HH:mm` |
| CheckinListScreen 标题变为"查寝签到"但仍含节假日 | 标题不改——节假日 section 用 section header 区分 |

### 推进顺序

**Step 1 — 数据层**：HolidayApi + HolidayResponse + HolidayModels + HolidayRepository + DI Module
- 退出信号：5 个接口方法编译通过，Repository 方法返回正确 Result 类型

**Step 2 — 首页集成**：HomeViewModel.loadHoliday() + HomeDashboardData.holidayTask + HolidayCard + shouldShowHoliday()
- 退出信号：HolidayCard 在窗口内可见，窗口外不可见；点击导航到 CHECKIN_LIST

**Step 3 — 列表页集成**：CheckinViewModel 节假日加载 + CheckinListScreen 双 section + TaskListItem sealed interface + onHolidayClick
- 退出信号：列表页同时展示查寝/节假日两个 section，点击查寝→详情页，点击节假日→表单页

**Step 4 — 导航**：Routes + NavGraph 新增 HOLIDAY_REGISTER 路由 + 刷新信号
- 退出信号：从列表点击节假日能导航到表单页（先空白页），提交成功后返回列表触发刷新

**Step 5 — 登记表单页**：HolidayRegisterViewModel + HolidayRegisterScreen（日期选择、表单字段、提交）
- 退出信号：完整表单流程——加载配置+字典+回填 → 填写 → 提交 → 返回刷新

### 测试设计

| 功能点 | 测试约束 | 验证方式 | 关键用例骨架 |
|--------|---------|---------|-------------|
| 首页卡片显示 | 窗口内显示，窗口外隐藏 | 手动 / 单元测试 shouldShowHoliday() | 构造 registerStartDate=昨天 registerEndDate=明天 → 显示；构造 registerEndDate=昨天 → 隐藏 |
| 列表双 section | 查寝和节假日各自渲染 | 手动 | 打开列表页，验证两个 section 都存在，各自有正确的标题和样式 |
| 导航链路 | 查寝→详情 / 节假日→表单 | 手动 | 点击两类 item，验证目标页面正确 |
| 表单字段验证 | 必填字段为空时不可提交 | 手动 | 留空必填字段点击提交，验证按钮置灰或弹提示 |
| 表单回填 | 已有记录时预填表单 | 手动（需联调构造数据） | getHolidayRegister 返回 data 非 null → 表单字段预填 |
| 提交成功刷新 | 提交后返回列表并刷新 | 手动 | 提交成功 → 自动返回列表 → 列表数据刷新 |
| 网络错误处理 | 各接口失败时显示中文提示 | 手动 | 断网 → 进入列表/表单 → 验证错误提示可读 |
| 鉴权 | 不带 role/roleId 能否调通 | 联调 | 发起任意 holiday 请求，检查是否返回 -100 |
| 数据模型正确性 | DTO 字段名与 HAR 一致 | 代码审查 | 对照接口分析 doc §2 逐字段核对 |

## 4. 与项目级架构文档的关系

- 关联架构 doc：`easysdd/architecture/designsystem-overview.md`（复用 LxCard 等设计系统组件）
- 关联既有 feature：`easysdd/features/2026-04-22-manual-zoom-scan/`（查寝模块的签到流程）
- 不需在 `DESIGN.md` 补引用——节假日是查寝模块的自然扩展，不引入新架构概念
- 不需新建子系统架构 doc——`feature/holiday/` 的模块边界在本文 §1 已说明
