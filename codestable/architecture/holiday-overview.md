---
doc_type: architecture
slug: holiday-overview
scope: feature/holiday/ 的节假日离返校登记任务、登记表单、与首页/查寝列表页的集成关系
summary: holiday 是 fdygl 域下的离返校登记功能，独立维护 HolidayApi / HolidayRepository / HolidayRegisterScreen，但入口寄生在首页卡片与查寝列表页双 section 中；网络层复用 @CheckinRetrofit 和 fdygl 多 header 鉴权。
status: current
last_reviewed: 2026-05-05
tags: [holiday, checkin, home, register, fdygl, date-picker]
depends_on: [checkin-overview, network-overview, home-overview]
---

# 节假日离返校登记模块总览

## 0. 术语

- **HolidayTask** — 节假日登记列表项领域模型，包含 `holidayId / name / registerStartDate / registerEndDate / isRegistered / allowStaySchool / startDate / endDate`。锚点：`app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:6-15`。
- **HolidayFormData** — 登记表单模型，包含开始/结束时间、离校/留校、事由、目的地、紧急电话、已有登记 ID。锚点：`app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:20-28`。
- **StrokeOption** — 离校/留校字典项，来自 `app_stroke_type` 字典接口。锚点：`app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:33-36` / `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:77-96`。
- **fdygl** — 辅导员管理域 `fdygl.aiit.edu.cn`。节假日与查寝同域，网络栈复用 `@CheckinRetrofit`。
- **两步日期时间选择器** — `HolidayDatePickerState` 管理的 BottomSheet 流程：先选日期，再选时分，最终输出 `yyyy-MM-dd HH:mm`。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayDatePicker.kt:55-80`。

## 1. 定位与受众

本 doc 描述 `feature/holiday/` 的职责边界：

- 节假日离返校登记任务列表查询、未登记状态判断、详情配置读取、已有登记回填、保存提交。
- 节假日登记表单页，以及自定义日期时间选择器。
- 与 `feature/home/` 首页卡片、`feature/checkin/` 查寝列表页双 section 的集成边界。

不覆盖：

- 查寝签到详情、拍照、定位签到主流程；见 `codestable/architecture/checkin-overview.md`。
- fdygl 多 header 鉴权细节；见 `codestable/architecture/network-overview.md`。
- 首页场景判定主结构；见 `codestable/architecture/home-overview.md`。

适用读者：

- 要改离返校表单字段、字典、提交体的人。
- 要排查节假日任务不显示、登记状态不准、或提交失败的人。
- 要理解为什么 holiday 独立成 feature 包，但入口出现在查寝列表页的人。

## 2. 结构与交互

### 2.1 模块内部三层

```
HolidayRegisterScreen / HolidayDatePicker
        ↓
HolidayRegisterViewModel
        ↓
HolidayRepository
        ↓
HolidayApi  --(@CheckinRetrofit)--> fdygl.aiit.edu.cn
        ↓
TokenManager.getUserCode() 作为 studentId
```

`feature/holiday/` 采用与其他 feature 一致的 `data / domain / ui` 三层：

| 层 | 文件 | 职责 |
|---|---|---|
| data | `HolidayApi.kt` | Retrofit 接口，封装 5 个 holiday/fdygl 接口。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayApi.kt:12-44` |
| data | `HolidayResponse.kt` | 后端 DTO，保留接口原字段形态。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayResponse.kt:6-124` |
| data | `HolidayRepository.kt` | studentId 注入、Result 包装、DTO→domain 映射、错误文案映射、Hilt API Provider。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:21-210` |
| domain | `HolidayModels.kt` | `HolidayTask / HolidayFormData / StrokeOption` 领域模型。锚点：`app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:6-36` |
| ui | `HolidayRegisterViewModel.kt` | 表单页状态、并发加载详情/已有记录/字典、提交。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:18-158` |
| ui | `HolidayRegisterScreen.kt` | 表单页 Compose UI。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterScreen.kt:50-110` |
| ui | `HolidayDatePicker.kt` | 两步日期时间选择 BottomSheet。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayDatePicker.kt:55-80` |

### 2.2 Retrofit 复用 checkin 网络栈

`HolidayModule` 通过 `@CheckinRetrofit` 创建 `HolidayApi`：

- 锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:202-209`

这意味着 holiday 不新建 Retrofit / OkHttp 栈，而是复用查寝所在的 fdygl 域配置和 `AuthInterceptor` fdygl 档。模块仍保留独立 `HolidayApi` / `HolidayRepository`，避免把 holiday 请求塞进 `CheckinRepository` 造成职责混杂。

### 2.3 五个后端接口

锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayApi.kt:14-43`

| 接口 | 方法 | 用途 | Repository 封装 |
|---|---|---|---|
| `app/holiday/getRegistrationPage` | POST | 分页查询登记任务 | `getRegistrationList(page)` |
| `app/holiday/getHolidaySetById` | GET | 获取节假日配置 | `getHolidayDetail(holidayId)` |
| `app/dict/list` | POST | 查询离校/留校字典 | `getStrokeTypes()` |
| `app/holiday/getHolidayRegister` | GET | 查询已有登记，表单回填 | `getExistingRegister(holidayId)` |
| `app/holiday/save` | POST | 保存/提交登记 | `saveRegistration(holidayId, form)` |

所有接口成功判断统一为 `code == "0"`。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:180-188`。

### 2.4 登记列表：列表接口 + 并发补登记状态

`getRegistrationList(page)` 先用 `TokenManager.getUserCode()` 作为 `studentId` 请求列表，再对每个 holiday 并发调用 `getHolidayRegister(holidayId, studentId)` 判断是否已登记：

- 主列表请求体：`studentId + pageNum`。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:27-34`。
- 并发补登记状态：`rows.map { async { ... getHolidayRegister ... } }.awaitAll()`。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:39-55`。
- DTO 到领域模型映射：`HolidayRow.toDomain(isRegistered)`。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:190-199`。

这样做的原因是列表接口本身不返回个人登记状态，首页和查寝列表页又需要区分已登记/未登记。

### 2.5 首页入口：只展示第一个未登记且处于登记窗口的任务

首页通过 `HomeViewModel.loadHoliday()` 读取 `HolidayRepository.getFirstUnregistered()`：

- `HomeDashboardData.holidayTask` 字段：`app/src/main/java/com/lightxin/feature/home/ui/HomeViewModel.kt:39`。
- `loadExtras()` 中与课表/查寝/跑步并发加载：`app/src/main/java/com/lightxin/feature/home/ui/HomeViewModel.kt:147-156` / `app/src/main/java/com/lightxin/feature/home/ui/HomeViewModel.kt:179-200`。
- `loadHoliday()` 调用仓库并返回 `(HolidayTask?, error)`：`app/src/main/java/com/lightxin/feature/home/ui/HomeViewModel.kt:271`。
- `HomeDashboard` 渲染前用 `shouldShowHoliday()` 过滤登记窗口：`app/src/main/java/com/lightxin/feature/home/ui/HomeDashboard.kt:205` / `app/src/main/java/com/lightxin/feature/home/ui/HomeDashboard.kt:508`。

holiday 没进 `HomeBootstrap` 冷启动预加载；它属于首页附加数据，加载失败不应阻塞首页主路径。该边界来自已完成的 feature 方案与验收：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md:36-38` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md:52-58`。

### 2.6 查寝列表页入口：fdygl 同源任务双 section

holiday 的列表入口寄生在 `CheckinListScreen`，而不是单独开一级导航或独立列表页：

- `CheckinViewModel` 持有 `holidayTasks / isLoadingHoliday / holidayError`，与查寝主任务状态隔离。锚点：`app/src/main/java/com/lightxin/feature/checkin/ui/CheckinViewModel.kt:16-26`。
- `CheckinViewModel` 在初始化时并发触发查寝列表和节假日列表加载。锚点：`app/src/main/java/com/lightxin/feature/checkin/ui/CheckinViewModel.kt:38-42`。
- holiday 加载失败只影响 holiday section，可独立重试。锚点：`app/src/main/java/com/lightxin/feature/checkin/ui/CheckinViewModel.kt:66-87` / `app/src/main/java/com/lightxin/feature/checkin/ui/CheckinViewModel.kt:122-124`。
- `CheckinListScreen` 通过 `onHolidayClick(holidayId)` 进入表单页。锚点：`app/src/main/java/com/lightxin/feature/checkin/ui/CheckinListScreen.kt:140-157`。

这个入口设计让用户仍从“查寝/学工在线任务”场景进入 fdygl 同源任务，同时保持 holiday 自己的数据层和表单页独立。

### 2.7 表单页：并发加载配置、已有记录、字典

`HolidayRegisterViewModel` 从路由参数读取 `holidayId`，初始化时并发加载三类数据：

- 节假日详情配置：控制标题、目的地/紧急电话字段显隐。
- 已有登记记录：有记录时回填表单。
- 离校/留校字典：渲染 radio 选项。

锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:44-63`。

加载结果合并策略：

- 三个请求全部失败或都无可用数据时，显示整页错误。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:69-80`。
- 只要有可用数据，就进入表单；字段显隐取 `leaveDestinationEnable / leaveEmergencyPhoneEnable`。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:82-97`。
- 提交时把当前 UI 字段组装为 `HolidayFormData`，交给 `saveRegistration()`；成功后设置 `submitSuccess`。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:125-153`。

`HolidayRegisterScreen` 监听 `submitSuccess`，成功后执行上层传入的 `onSubmitSuccess()`。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterScreen.kt:62-66`。

### 2.8 导航与刷新信号

holiday 新增一条参数化路由：

- 路由定义：`Routes.HOLIDAY_REGISTER = "holiday/register/{holidayId}"`。锚点：`app/src/main/java/com/lightxin/navigation/Routes.kt:16-18`。
- 查寝列表页点击 holiday item 后导航到 `Routes.holidayRegister(holidayId)`。锚点：`app/src/main/java/com/lightxin/navigation/NavGraph.kt:154-155`。
- `NavGraph` 注册 `HolidayRegisterScreen` composable。锚点：`app/src/main/java/com/lightxin/navigation/NavGraph.kt:164-168`。

提交成功后的刷新链路由 `NavGraph` 承载：表单页回调 → 返回查寝列表 → 设置列表页刷新信号；查寝列表页消费该信号后重新加载 holiday section。该行为已在验收中确认：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md:46-50` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md:73-80`。

## 3. 数据与状态

### 3.1 关键领域模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `HolidayTask` | 列表项，包含登记窗口和已登记状态 | `app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:6-15` |
| `HolidayFormData` | 表单提交与回填模型 | `app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:20-28` |
| `StrokeOption` | 离校/留校字典项 | `app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:33-36` |
| `HolidayRegisterUiState` | 表单页完整 UI 状态 | `app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:18-36` |

### 3.2 后端 DTO 的边界

`HolidayResponse.kt` 保留后端字段原貌，包括：

- `allowStaySchool / isApproval / registrationType`
- `leaveDestinationEnable / leaveDestinationRequire`
- `leaveEmergencyPhoneEnable / leaveEmergencyPhoneRequire`
- `leaveProofMaterialsEnable / leaveProofMaterialsRequire`
- `stayProofMaterialsEnable / stayProofMaterialsRequire`

锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayResponse.kt:15-38` / `app/src/main/java/com/lightxin/feature/holiday/data/HolidayResponse.kt:50-69`。

当前 UI 只消费目的地和紧急联系电话的 Enable 标记；审批、附件、Require 标记暂不形成 UI 行为。该边界在验收中确认：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md:52-58`。

### 3.3 与其他模块共享的状态

| 状态 | 所属模块 | 用途 |
|---|---|---|
| `HomeDashboardData.holidayTask` | `feature/home/ui/HomeViewModel.kt:39` | 首页展示第一个未登记、窗口内 holiday 任务 |
| `CheckinUiState.holidayTasks` | `feature/checkin/ui/CheckinViewModel.kt:19` | 查寝列表页 holiday section |
| `HolidayRegisterUiState` | `feature/holiday/ui/HolidayRegisterViewModel.kt:18-36` | 表单页状态 |
| `checkin_refresh` | `navigation/NavGraph.kt` 里的列表页返回信号 | 表单提交成功后刷新查寝/holiday 列表 |

## 4. 关键决策

- **holiday 独立成 `feature/holiday/`，不塞进 `feature/checkin/`** —— 它与查寝同属 fdygl 域，但业务模型、表单、接口都独立；查寝列表页只承载入口 section。来源：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md:44-51`。
- **复用 `@CheckinRetrofit`** —— holiday 与 checkin 同域同鉴权档，新建 HolidayRetrofit 会重复网络配置。来源：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:202-209`。
- **列表页寄生在 CheckinListScreen 双 section** —— 不新建独立 holiday 列表页，避免增加导航层级；查寝和 holiday 的加载/错误/重试状态仍相互隔离。来源：`app/src/main/java/com/lightxin/feature/checkin/ui/CheckinViewModel.kt:16-26,66-87,122-124` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md:49-51`。
- **首页不走 HomeBootstrap** —— holiday 是附加提醒，不是首页主路径依赖；加载失败不阻塞首页。来源：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md:36-38` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md:52-55`。
- **列表状态通过逐项 `getHolidayRegister` 补齐** —— 列表接口不返回个人登记状态，只能对每个 holiday 查询已有登记。来源：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:39-55`。
- **日期时间选择器自定义两步 BottomSheet** —— 避免 Material3 DatePicker 默认视觉与项目陶土色系割裂。来源：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md:52-55` / `app/src/main/java/com/lightxin/feature/holiday/ui/HolidayDatePicker.kt:55-80`。

## 5. 代码锚点

- `app/src/main/java/com/lightxin/feature/holiday/data/HolidayApi.kt:12-44` — 5 个 Retrofit 接口。
- `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:21-25` — Repository 依赖 `HolidayApi + TokenManager`。
- `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:27-60` — 登记列表 + 并发补状态。
- `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:77-96` — 离校/留校字典。
- `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:98-124` — 已有登记回填。
- `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:126-157` — 保存登记。
- `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:202-209` — `@CheckinRetrofit` Provider。
- `app/src/main/java/com/lightxin/feature/holiday/domain/HolidayModels.kt:6-36` — 领域模型。
- `app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:53-99` — 表单初始化并发加载。
- `app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:125-153` — 表单提交。
- `app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterScreen.kt:50-110` — 页面骨架 + BottomSheet 挂载。
- `app/src/main/java/com/lightxin/feature/holiday/ui/HolidayDatePicker.kt:55-80` — 日期时间选择状态。
- `app/src/main/java/com/lightxin/feature/home/ui/HomeViewModel.kt:271` — 首页 holiday 加载。
- `app/src/main/java/com/lightxin/feature/home/ui/HomeDashboard.kt:205,508` — 首页卡片展示与窗口判断。
- `app/src/main/java/com/lightxin/feature/checkin/ui/CheckinViewModel.kt:16-26,66-87,122-124` — 查寝列表 holiday section 状态与加载。
- `app/src/main/java/com/lightxin/navigation/Routes.kt:16-18` — holiday 路由。
- `app/src/main/java/com/lightxin/navigation/NavGraph.kt:154-168` — holiday 导航挂载。

## 6. 已知约束 / 边界情况

- **studentId 来自 `TokenManager.getUserCode()`** —— holiday 请求不单独维护学生 ID 来源。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:165-166`。
- **接口成功只认 `code == "0"`** —— `flag` 字段存在但当前不作为成功判断。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:180-188`。
- **首页只展示第一个未登记 holiday** —— `getFirstUnregistered()` 取第一页第一个 `!isRegistered` 项。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:159-163`。
- **holiday 列表不做滚动分页** —— 虽然 Repository 接收 page 参数，但当前首页与查寝列表只取 page 1；该边界来自验收。锚点：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md:52-56`。
- **审批流程和附件字段保留但不实现 UI** —— DTO 保留字段，表单页不渲染审批/附件。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayResponse.kt:20-37,54-68` / `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md:56-58`。
- **已有登记 `data == null` 表示可新建** —— Repository 返回 `HolidayFormData?`，null 进入空表单。锚点：`app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:98-120`。
- **表单字段显隐看 Enable，不看 Require** —— 当前 `destinationEnabled` / `urgentPhoneEnabled` 分别由 `leaveDestinationEnable` / `leaveEmergencyPhoneEnable` 控制。锚点：`app/src/main/java/com/lightxin/feature/holiday/ui/HolidayRegisterViewModel.kt:88-89`。

## 7. 相关文档

- fdygl 同源任务在查寝列表页双 section 承载：`codestable/architecture/checkin-overview.md` §2.6。
- 多域名网络层和 fdygl 多 header 鉴权：`codestable/architecture/network-overview.md`。
- 首页数据加载与 dashboard 展示：`codestable/architecture/home-overview.md`。
- 节假日离返校登记方案：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md`。
- 节假日离返校登记验收：`codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-acceptance.md`。
- 架构总入口：`codestable/architecture/DESIGN.md`
