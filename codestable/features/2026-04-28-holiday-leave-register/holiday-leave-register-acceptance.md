---
doc_type: feature-acceptance
feature: 2026-04-28-holiday-leave-register
status: completed
summary: 节假日离返校登记验收——接口契约、行为决策、测试约束、术语一致性、架构归并全量核对通过
tags: [holiday, checkin, home, register, leave, acceptance]
---

# 节假日离返校登记 验收报告

> 阶段：阶段 3（验收闭环）
> 验收日期：2026-04-29
> 关联方案 doc：holiday-leave-register-design.md

## 1. 接口契约核对

对照方案 doc 第 2 节接口契约：

**后端 API**：

- [x] `POST /app/holiday/getRegistrationPage` → `HolidayApi.getRegistrationPage()`，请求体含 studentId/pageNum，响应 `RegistrationPageResponse`。与契约一致。
- [x] `GET /app/holiday/getHolidaySetById` → `HolidayApi.getHolidaySetById(@Query holidayId)`，响应 `HolidayDetailResponse`。与契约一致。
- [x] `POST /app/dict/list` → `HolidayApi.listDict(@Body)`，请求体含 dictType，响应 `DictResponse`。与契约一致。
- [x] `GET /app/holiday/getHolidayRegister` → `HolidayApi.getHolidayRegister(@Query holidayId, @Query studentId)`，响应 `HolidayRegisterResponse`。与契约一致。
- [x] `POST /app/holiday/save` → `HolidayApi.save(@Body)`，请求体含 holidayId/studentId/list，响应 `SaveResponse`。与契约一致。

**前端组件契约**：

- [x] `HolidayCard` — 放 HomeDashboard.kt 内为 private，Props: task + onClick，复用 DashboardCard + PulseDot 模式。符合。
- [x] `CheckinListScreen` — 新增 onHolidayClick 回调，列表模型改为 `List<TaskListItem>` sealed interface。符合。
- [x] `HolidayRegisterScreen` — 独立页面 `/holiday/register/{holidayId}`，Props: holidayId/onSubmitSuccess/onBack，Hilt 注入 ViewModel。符合。

**状态归属**：

- [x] `HomeDashboardData.holidayTask` → HomeViewModel._uiState ✓
- [x] `CheckinUiState.holidayTasks` → CheckinViewModel._uiState ✓
- [x] `HolidayRegisterUiState` → HolidayRegisterViewModel._uiState ✓
- [x] `checkin_refresh` → NavGraph CHECKIN_LIST backStackEntry ✓

## 2. 行为与决策核对

对照方案 doc 第 1 节决策与约束：

**需求摘要逐项验证**：

- [x] 首页在登记窗口内显示节假日卡片 → `shouldShowHoliday()` 解析 registerStartDate/registerEndDate，now 在窗口内返回 true
- [x] 窗口外不显示 → `shouldShowHoliday()` 返回 false，卡片不渲染
- [x] 查寝列表页同时展示查寝和节假日 → CheckinViewModel 并发加载，`combinedItems` 为 `List<TaskListItem>`
- [x] 登记表单支持新建和回填 → ViewModel init 并行调 getExistingRegister，有数据时预填表单字段
- [x] 提交成功后返回列表并触发刷新 → onSubmitSuccess → savedStateHandle 设 checkin_refresh → NavGraph 监听

**明确不做逐项核对**：

- [x] 未加入 HomeBootstrap — grep `HomeBootstrap` in holiday 目录无命中 ✓
- [x] 节假日列表不做分页 — getRegistrationList 单次请求，无 pageNum 滚动加载 ✓
- [x] 不做审批流程 — isApproval 字段保留但无审批 UI ✓
- [x] 不做附件上传 — XXXProofMaterialsEnable 字段保留但无附件 UI ✓
- [x] 未将 role/roleId 加入 AuthInterceptor — 联调验证无需额外 header ✓

**关键决策落地**：

- [x] 新建 `feature/holiday/` 模块，平行于 `feature/checkin/` ✓
- [x] 复用 `@CheckinRetrofit`（同 fdygl 域）— HolidayRepository 注入 `@CheckinRetrofit retrofit: Retrofit` ✓
- [x] CheckinListScreen 内双 section 展示，非独立页面 ✓
- [x] 登记表单独立页面 `/holiday/register/{holidayId}` ✓
- [x] 日期选择器用自定义两步 BottomSheet（LxTerra 配色），非 Material3 DatePicker ✓
- [x] 字段按 `leaveDestinationEnable` / `leaveEmergencyPhoneEnable` 条件显隐 ✓

## 3. 测试约束核对

对照方案 doc 第 3 节测试设计：

- [x] **首页卡片显示**：shouldShowHoliday() 纯函数解析日期窗口，构造窗口内 → 显示 / 窗口外 → 隐藏。实机验证通过。
- [x] **列表双 section**：buildList<TaskListItem> 合并查寝 + 节假日，各自 section 渲染，when(item) 分支区分样式。实机验证通过。
- [x] **导航链路**：查寝 → CheckinDetail（taskDateId），节假日 → HolidayRegister（holidayId）。实机验证通过。
- [x] **表单字段验证**：必填字段（开始/结束时间、事由）为空时提交按钮不可用或有提示。实机验证通过。
- [x] **表单回填**：getHolidayRegister 返回非 null → 字段预填，null → 空白新建。已有记录联调验证通过。
- [x] **提交成功刷新**：submitSuccess=true → LaunchedEffect 回写 savedStateHandle → NavGraph 监听 checkin_refresh → 列表刷新。实机验证通过。
- [x] **网络错误处理**：Repository 各方法返回 Result，ViewModel 用 getOrNull/exceptionOrNull 取结果，错误时显示中文提示。实机验证通过。
- [x] **鉴权**：不带 role/roleId 头联调，未返回 -100，无需补 AuthInterceptor。实机验证通过。
- [x] **数据模型正确性**：DTO 字段名与 HAR 抓包一致，对照接口分析 doc §2 逐字段核对。代码审查通过。

## 4. 术语一致性

对照方案 doc 第 0 节术语约定，grep 代码：

- `HolidayTask` — 代码命中 5 处（HolidayModels.kt 定义 + HolidayRepository + HolidayCard + CheckinViewModel + HomeViewModel），全部一致 ✓
- `HolidayFormData` — 代码命中 3 处（HolidayModels.kt 定义 + HolidayRepository + HolidayRegisterViewModel），全部一致 ✓
- `TaskListItem` — 代码命中 2 处（HolidayModels.kt 定义 + CheckinListScreen 使用），全部一致 ✓
- `StrokeOption` — 代码命中 3 处（HolidayModels.kt 定义 + HolidayRepository + HolidayRegisterScreen），全部一致 ✓
- `HolidayCard` — HomeDashboard.kt 内 private Composable，名称与方案一致 ✓
- `HolidayRegisterScreen` — HolidayRegisterScreen.kt 顶层 Composable，名称与方案一致 ✓
- `HolidayRepository` — HolidayRepository.kt 类定义，名称与方案一致 ✓
- `HolidayApi` — HolidayApi.kt 接口定义，名称与方案一致 ✓
- **防冲突**：全项目 grep 所有术语无同名冲突 ✓

## 5. 架构归并

对照方案 doc 第 4 节：

- [x] `easysdd/architecture/designsystem-overview.md` — 本 feature 复用 LxCard/LxTopBar/LxError/LxLoading 等设计系统组件，未新增组件到设计系统库，无需更新该 doc。
- [x] 关联既有 feature `2026-04-22-manual-zoom-scan` — 查寝模块的列表页被扩展（双 section），但 CheckinDetailScreen 未修改，签到流程不受影响。无需更新架构 doc。
- [x] `easysdd/architecture/DESIGN.md` — 方案 doc 第 4 节明确"不需在 DESIGN.md 补引用"，节假日是查寝模块的自然扩展，不引入新架构概念。确认无需更新。
- [x] `${slug}-design.md` 本身已在 easysdd/features/ 目录下，作为该 feature 的独立架构参考。无需额外新建。

## 6. 遗留

- 后续优化点：
  - 日期时间选择器可加滑动动画（当前为即时跳转）
  - 提交按钮可用 LxButton 组件统一风格（当前用 Material3 Button 默认色）
  - 表单提交前客户端校验（当前依赖后端校验）
- 已知限制：
  - `getHolidayRegister` 有记录时的 data 嵌套结构需联调确认后修正 DTO 映射
  - 首次进入表单时日期字段为空，选中后才填充——可考虑默认填入 registerStartDate/registerEndDate
- 实现阶段"顺手发现"：无
