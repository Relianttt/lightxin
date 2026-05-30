---
doc_type: architecture
slug: schedule-overview
scope: feature/schedule/ 的周课表获取、三态周选择器、动态节次课程网格渲染，以及与首页 SceneResolver 的共享边界
summary: schedule 以 ScheduleRepository 封装周次信息与课表数据，UI 层用 LazyRow 胶囊周选择器 + 支持第 0 节的绝对定位网格渲染 7 天课程，详情走 ModalBottomSheet
status: current
last_reviewed: 2026-05-31
tags: [schedule, course, timetable, week-selector, grid, section-zero]
depends_on: [network-overview, home-overview]
---

# 课表模块总览

## 0. 术语

- **三态周选择器** — 周次胶囊支持三种视觉状态：普通、当前周、选中周；选中周填充陶土色，当前周有边框，普通周透明
- **周次信息** — `WeekInfo` 包含当前周、总周数、学年、学期，由 `getWeekList` 接口返回
- **动态节次范围** — 常规课表渲染 1-10 节；当任一课程 `startSection <= 0` 时，网格从第 0 节开始渲染，并把第 0 节标签显示为“早”

## 1. 定位与受众

本 doc 描述 `feature/schedule/` 的职责边界：

- 从后端获取周次列表与课表数据，在本地映射为 `Course` 领域模型
- 渲染周课表网格 + 周次选择器 + 课程详情弹窗
- 作为数据源被 `feature/home/` 的 `SceneResolver` 消费（课程数据由 `ScheduleRepository` 暴露）

适用读者：

- 要改课表网格布局、周选择器交互、或课程详情展示的人
- 要理解课表数据如何被首页场景引擎消费的人
- 要排查"课表为什么显示不对"或"周次切换后数据没更新"的人

## 2. 结构与交互

### 2.1 三层结构

```
ScheduleApi (data)         → POST dict/selectZcList.do
                              POST mobile/selectStuSelfTimeTable.do
ScheduleRepository (data)  → 周次 → 课表 两段式加载
ScheduleViewModel (ui)     → 周次 → 课表状态机
ScheduleScreen (ui)        → 周选择器 + 网格 + 详情弹窗
```

### 2.2 两段式加载：先拿周次，再拿课表

锚点：`feature/schedule/data/ScheduleRepository.kt:22-86`

`ScheduleViewModel` init 时：

1. `loadWeekInfo()` — 调用 `repository.getWeekInfo()`
2. 成功后自动调用 `loadCourses(schoolYear, schoolTerm, currentWeek)`
3. 用户切周时调用 `onWeekSelected(week)` → 再次 `loadCourses()`

这种两段式设计的原因是后端将周次信息与课表数据分离在两个接口中，且课表查询依赖周次接口返回的学年/学期参数。

### 2.3 课表按天分组，课程块绝对定位

锚点：`feature/schedule/data/ScheduleRepository.kt:56-85`

`ScheduleRepository.getCourses()` 将后端返回的平铺数据结构映射为 `Course` 列表：
- 按 `dayOfWeek`（1-7）区分七天
- 每门课记录 `startSection` / `endSection`，用于网格中计算高度与偏移

UI 层用 `CELL_HEIGHT = 58.dp` 为基准，先根据课程数据计算 `firstSection`：没有第 0 节课程时为 1；存在 `startSection <= 0` 时为 0。课程块按 `top = (startSection - firstSection) * CELL_HEIGHT` 绝对定位，避免第 0 节课程产生负偏移或崩溃。

锚点：`feature/schedule/ui/ScheduleScreen.kt:238-320`。

### 2.4 三态周选择器

锚点：`feature/schedule/ui/ScheduleScreen.kt:128-183`

`WeekSelector` 使用 `LazyRow` 渲染周次胶囊，支持：
- **选中态**（`isSelected`）— 陶土色填充，白字
- **当前周态**（`isCurrent`）— 灰边框，深灰字
- **普通态** — 透明底，浅灰字

三态组合实际产生四种视觉结果（选中+当前周时选中优先）。

`LazyRow` 在选中周变化时自动 `animateScrollToItem(selectedWeek - 2)`，让选中周保持在可视区中央偏左位置。同时通过 `derivedStateOf` 计算"本周"指示器（`← 本周` / `本周 →`），在当前周不在可视区时显示方向提示。

### 2.5 课程详情走 ModalBottomSheet

锚点：`feature/schedule/ui/ScheduleScreen.kt:114-123`

点击课程块弹起 `ModalBottomSheet`，展示课程名（Newsreader 衬线字）、时间、教室、教师信息。非全屏弹窗，`sheetState` 使用默认值。

### 2.6 课程颜色基于名称 hash

锚点：`feature/schedule/ui/ScheduleScreen.kt:70-73`

`courseColor(name)` 通过 `name.hashCode()` 从 `LxCategoryColors` 取色，保证同一课程名在不同周次渲染同一颜色。这是纯 UI 层约定，不与后端返回的颜色字段关联。

### 2.7 首页对课表的消费

`feature/home/` 通过 `ScheduleRepository` 获取今日课程列表，在 `SceneResolver.resolve()` 中判断当前场景。课表数据进入 splash 预加载阶段（`HomeBootstrap`），与查寝数据一同在冷启动时预取。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `WeekInfo` | currentWeek / totalWeeks / schoolYear / schoolTerm | `ScheduleModels.kt:3-8` |
| `Course` | name / startSection / endSection / room / teacher / dayOfWeek | `ScheduleModels.kt:10-17` |
| `ScheduleUiState` | weekInfo / courses / selectedWeek / isLoading / error | `ScheduleViewModel.kt:15-21` |

### 3.2 状态所有权

- `ScheduleUiState` 由 `ScheduleViewModel` 持有
- `selectedWeek` 是 ViewModel 内部状态，用户切换时触发重新加载，不持久化
- `WeekInfo` 在 ViewModel init 时加载一次，后续不再刷新

## 4. 关键决策

- **课程块用绝对定位而非 LazyColumn** —— 网格按 10 节固定高度渲染，不虚拟化。因为课表数据量小（最多 7 天 × 每天数门课），绝对定位更直观。
- **课程颜色用 name.hashCode() 取色** —— 确保同一课程颜色稳定，不与后端绑定。来源：`ScheduleScreen.kt:70-73`。
- **课表数据进入 splash 预加载** —— 首页冷启动时 `HomeBootstrap` 预取课表数据，让首屏可直接展示。来源：`feature/home/domain/HomeBootstrap.kt:54-68`。
- **课表不走独立 NavHost 路由** —— 课表是首页三 tab 的第二个标签，由 `HomeScreen` 内部管理；当前 `Routes.kt` 不再定义独立 `SCHEDULE` 常量。

## 5. 代码锚点

- `feature/schedule/data/ScheduleApi.kt:9-23` — 周次与课表两个接口
- `feature/schedule/data/ScheduleRepository.kt:22-86` — 两段式加载主链
- `feature/schedule/data/ScheduleResponse.kt:8-46` — 响应模型
- `feature/schedule/domain/ScheduleModels.kt:3-17` — WeekInfo 与 Course
- `feature/schedule/ui/ScheduleViewModel.kt:24-81` — 状态管理与加载
- `feature/schedule/ui/ScheduleScreen.kt:68-384` — 周选择器 + 网格 + 详情
- `feature/home/domain/SceneResolver.kt` — 首页场景引擎消费课表

## 6. 已知约束 / 边界情况

- **课表数据不缓存到本地** —— 每次进入或切换周次都重新请求后端。无离线课表能力。
- **最大节次仍按 10 节处理** —— `SECTION_COUNT = 10` 仍是网格最大常量；常规显示 1-10，遇到第 0 节课程时显示 0-10（第 0 节标签为“早”）。来源：`ScheduleScreen.kt:61,238-320`。
- **第 0 节只影响课表页网格起点** —— 首页课程时间判断仍由 `feature/home/domain/SectionSchedule.kt` 维护，不从课表 UI 的动态 `firstSection` 反推。
- **课程详情中的 `LxDetailRow` 使用固定 48dp labelWidth** —— 适用于当前字段长度，若新增字段需要调整。
- **`selectedWeek` 在 ViewModel 重建时回到 init 值** —— 不持久化，进程重建后从当前周开始。

## 7. 相关文档

- 首页场景引擎：`codestable/architecture/home-overview.md`
- 网络层：`codestable/architecture/network-overview.md`
- 首页启动预加载：`codestable/architecture/home-overview.md` §2.2
- 架构总入口：`codestable/architecture/DESIGN.md`
