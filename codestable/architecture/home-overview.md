---
doc_type: architecture
slug: home-overview
scope: 首页模块的启动预加载、场景判定、副标题轮换、以及 HomeDashboard 实际渲染骨架
summary: home 以 MainActivity + HomeBootstrap 完成冷启动首屏预加载，以 HomeViewModel 聚合主页状态，并用 SceneResolver/SubtitleLibrary 把课程与查寝信号转成叙事文案；UI 层当前是固定骨架，不是单卡片状态机
status: current
last_reviewed: 2026-04-21
tags: [home, bootstrap, splash, scene, subtitle]
depends_on: [network-overview, running-overview]
---

# 首页模块总览

## 0. 术语

- **冷启动快照** — `HomeBootstrapSnapshot`，由 `HomeBootstrap` 在 App 冷启动阶段一次性填充，给首页首帧提供课表 / 查寝 / 用户名基础数据
- **场景判定** — `SceneResolver.resolve()` 对“当前时刻 + 今日课程 + 查寝任务”的纯函数计算，只决定首页当前处于哪种 `HomeScene`
- **副标题轮换** — `HomeViewModel` 用 `subtitleRotation` 驱动同一文案桶内切换；`SubtitleLibrary` 本身不维护状态
- **首页叙事骨架** — 当前实际渲染结构是“问候区 + 分割线 + 下一节 headline + 固定卡片区”，不是设计稿里的“空态 / 单智慧卡片”二态页面

## 1. 定位与受众

本 doc 描述 `feature/home/` 在当前代码里的真实职责边界：

- 冷启动后第一个需要快速可用的数据编排面，承担的不只是 tab 容器职责
- 分成”启动预加载””二阶段聚合””纯规则文案””固定渲染骨架”四层，未把所有信息塞进单个 ViewModel
- 对外承接 `MainActivity` 的 splash 放行、对内消费 `schedule / checkin / running / labor` 四个 feature 的摘要数据

适用读者：

- 要继续改首页叙事结构、预加载、或副标题策略的人
- 要判断某类数据是否应该进 splash 预取的人
- 要排查“首页文案为什么这样算”“为什么某块没显示”的人

## 2. 结构与交互

### 2.1 `HomeScreen` 是三 tab 容器，`HomeDashboard` 才是首页主页面

`HomeScreen` 自己只做三件事：

- 管理 `首页 / 课程表 / 我的` 三个一级 tab
- 把 tab 0 绑定到 `HomeDashboard`
- 在“我的”页挂出查寝、劳教、AI 课堂、路线模拟、关于、退出登录等跳转

锚点：`feature/home/ui/HomeScreen.kt:43-49` / `feature/home/ui/HomeScreen.kt:51-146`。

首页模块在架构上分两层：

- `HomeScreen`：导航容器
- `HomeDashboard`：首页叙事与数据呈现

### 2.2 冷启动阶段由 `MainActivity + HomeBootstrap` 共同决定 splash 放行

`MainActivity.onCreate()` 把 splash 控制与真实加载拆成两条协程，避免在主页 ViewModel 里阻塞等数据：

1. 一条协程调用 `homeBootstrap.load()` 真正拉数据
2. 另一条协程等待 `homeBootstrap.ready.first { it }`
3. 若 1500ms 内没 ready，也会超时放行 splash，但不会取消加载协程

锚点：`MainActivity.kt:28-43` / `MainActivity.kt:69-70`。

`HomeBootstrap` 本身是单例预加载协调器，特点是：

- `Mutex + loaded` 保证 `load()` 只执行一次
- 未登录时直接返回，但 `finally` 仍会置 `ready = true`
- 只并发预取 `schedule + checkin`，并同步读取本地 `userName`
- 把结果汇总成 `HomeBootstrapSnapshot` 写入 `snapshot`

锚点：`feature/home/domain/HomeBootstrap.kt:30-72` / `feature/home/domain/HomeBootstrap.kt:123-130`。

这样分的目的很明确：首屏只赌“最值得预先拿到的主页基础信号”，而不是把所有首页数据都塞进 splash 阻塞阶段。

### 2.3 `HomeViewModel` 负责二阶段聚合，而不是包办全部首屏数据

`HomeViewModel` 的初始化顺序是：

1. 先从 `TokenManager` 填本地 `userName`
2. 若 `HomeBootstrap.snapshot` 已存在，立即 `applyBootstrap()`
3. 继续订阅后续 snapshot，接住“splash 已放行但数据稍后才到”的情况
4. 另外并发加载 bootstrap 不覆盖的 `running + labor`

锚点：`feature/home/ui/HomeViewModel.kt:68-81` / `feature/home/ui/HomeViewModel.kt:118-161`。

分层如下：

- 冷启动基础层：课表、查寝、用户名
- 首屏补充分层：跑步、劳教

`refresh()` 跳过 bootstrap，直接重拉四类摘要数据，并先递增 `subtitleRotation`，保证手动刷新带来文案轮换。

锚点：`feature/home/ui/HomeViewModel.kt:83-93` / `feature/home/ui/HomeViewModel.kt:163-200`。

### 2.4 场景引擎是纯函数，优先级写死在 `SceneResolver`

`SceneResolver.resolve()` 不读仓储、不读时钟状态流，只接收：

- `now`
- `todayCourses`
- `nextUnsignedCheckin`

然后按固定优先级返回一个 `HomeScene`：

1. `PreClass`
2. `PreNextAfterClass`
3. `InClass`
4. `EveningCheckin`
5. `LunchBooks`
6. `MorningBooks`
7. `None`

锚点：`feature/home/domain/SceneResolver.kt:9-23` / `feature/home/domain/SceneResolver.kt:41-57`。

具体时间判断又统一依赖 `SectionSchedule` 的固定节次表，而不是散落在 UI 层手写时间常量：

- 1-11 节对应固定起止时刻
- 下午首节固定为第 5 节
- 晚间首节固定为第 9 节

锚点：`feature/home/domain/SectionSchedule.kt:13-35`。

这让“首页场景”成为一套可测试的领域规则，而不是 Compose 组件里的 if/else 拼装。

### 2.5 副标题由两层机制组成：场景强文案优先，文案库桶选择兜底

`HomeViewModel.buildSubtitle()` 的顺序是：

1. 先用 `SceneResolver.resolve()` 算场景
2. 若 `SubtitleLibrary.fromScene(scene)` 能给出结果，就直接返回
3. 否则再用 `SubtitleLibrary.pickSubtitle(context, rotation)` 从通用文案桶里挑一条

锚点：`feature/home/ui/HomeViewModel.kt:203-217`。

`SubtitleLibrary` 只做“根据上下文挑哪条文案”，不维护切换状态；`rotation` 由 ViewModel 外部注入。它当前覆盖的桶包括：

- 深夜 / 明日早课 / 待查寝
- 全天无课的多个时段桶
- 当日课程结束后桶
- 普通上午 / 中午 / 下午 / 傍晚 / 夜间桶

锚点：`feature/home/domain/SubtitleLibrary.kt:17-37` / `feature/home/domain/SubtitleLibrary.kt:61-97` / `feature/home/domain/SubtitleLibrary.kt:99-175`。

### 2.6 文案轮换触发点分散在 ViewModel 与 Compose 生命周期层

当前副标题有三类触发器：

- 手动下拉刷新：`refresh()` 里先 `subtitleRotation++`
- 后台回前台且超过 30 分钟：`onResumed()` 里递增 rotation
- 前台每分钟定时重算：`HomeDashboard` 的 ticker 调 `recomputeSubtitle()`

锚点：`feature/home/ui/HomeViewModel.kt:83-93` / `feature/home/ui/HomeViewModel.kt:95-116` / `feature/home/ui/HomeViewModel.kt:269-270` / `feature/home/ui/HomeDashboard.kt:97-107`。

这里要注意一个边界：分钟级重算只会重进 `buildSubtitle()`，不会自动递增 `subtitleRotation`；它负责“跨分钟导致场景变化”，不是“同桶随机轮换”。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `HomeBootstrapSnapshot` | 冷启动一次性快照 | `feature/home/domain/HomeBootstrap.kt:123-130` |
| `HomeDashboardData` | 首页聚合后的领域摘要 | `feature/home/ui/HomeViewModel.kt:32-39` |
| `HomeUiState` | Compose 订阅的完整首页状态 | `feature/home/ui/HomeViewModel.kt:41-48` |
| `HomeScene` | 场景判定结果枚举族 | `feature/home/domain/HomeScene.kt:12-33` |
| `SubtitleContext` | 通用副标题挑选输入 | `feature/home/domain/SubtitleLibrary.kt:10-15` |

### 3.2 状态所有权

- `HomeBootstrap.ready` / `snapshot` 由 `HomeBootstrap` 单例持有，跨 ViewModel 生命周期复用
- `HomeUiState` 由 `HomeViewModel` 持有，是页面当前唯一直接渲染输入
- `subtitleRotation` 与 `lastPausedAt` 只存在于 `HomeViewModel` 内存态，不持久化

锚点：`feature/home/domain/HomeBootstrap.kt:35-42` / `feature/home/ui/HomeViewModel.kt:60-67`。

### 3.3 数据来源边界

首页当前消费四类后端摘要：

- 课表：`ScheduleRepository`
- 查寝：`CheckinRepository`
- 跑步：`RunningRepository`
- 劳教：`LaborRepository`

锚点：`feature/home/ui/HomeViewModel.kt:51-58`。

但它们并不在同一阶段进入页面：

- 冷启动阶段只预取课表 / 查寝
- 首页补充阶段再取跑步 / 劳教

锚点：`feature/home/domain/HomeBootstrap.kt:54-68` / `feature/home/ui/HomeViewModel.kt:140-161`。

## 4. 实际渲染骨架

### 4.1 `HomeDashboard` 当前是固定结构，单智慧卡片状态机尚未落成

在非 loading 态下，`HomeDashboard` 的主体结构固定为：

- `GreetingSection`
- `SectionRule`
- `NextClassHeadline`
- `TodayCourseCard`
- `RunningCard`
- 条件渲染的 `CheckinCard`

锚点：`feature/home/ui/HomeDashboard.kt:114-200`。

当前代码已引入“场景”“副标题”“下一节 headline”的叙事层，但 UI 仍是“固定骨架 + 条件插卡”。设计稿预期为“无提醒时只保留问候语 / 有提醒时只出现一张智慧卡片”的完全状态机，当前尚未落成。

锚点：`docs/项目规划/homepage-design-spec.md:10-15` / `docs/项目规划/homepage-design-spec.md:126-136` / `feature/home/ui/HomeDashboard.kt:156-200`。

### 4.2 场景系统主要体现在问候副标题，不直接驱动独立场景卡组件

当前 `GreetingSection` 使用 `AnimatedContent` 切换副标题，动画形式与设计稿约束一致：旧文案上移淡出，新文案下移淡入，大标题不动。

锚点：`feature/home/ui/HomeDashboard.kt:208-257` / `docs/项目规划/homepage-design-spec.md:81-99`。

场景目前不映射为”MorningBooksCard / InClassCard / LunchBooksCard”这类独立 UI 组件，而是体现在：

- 副标题文本
- `NextClassHeadline`
- 查寝卡片是否进入首页

### 4.3 `NextClassHeadline` 是当前首页叙事主轴之一

`NextClassHeadline` 根据 `courses + tomorrowFirstSection + now` 输出四种主句式：

- `今天没有课程安排`
- `正在上 · 课程名 · 教室`
- `下一节 · 课程名 · 教室 · 时刻`
- `明天第 n 节开课` / `今天的课全部结束`

锚点：`feature/home/ui/HomeDashboard.kt:276-335`。

它并不读取 `HomeScene`，而是独立根据课表时间关系生成 headline。这是首页现有叙事结构里一条与“副标题场景系统”并行的表达通道。

### 4.4 查寝卡片有独立可见性窗口，不等于场景优先级

`CheckinCard` 只有在 `shouldShowCheckin(task)` 返回 true 时才渲染。该逻辑按任务开始时间做“前后 4 小时”窗口判断，不直接复用 `SceneResolver` 的 `EveningCheckin` 规则。

锚点：`feature/home/ui/HomeDashboard.kt:190-199` / `feature/home/ui/HomeDashboard.kt:425-442`。

因此首页存在两套和查寝有关的展示规则：

- 场景层：22:00 后未签到可触发 `EveningCheckin` 副标题
- 卡片层：开始前后 4 小时窗口内可显示查寝卡

两者职责不同，不能混为一条逻辑。

## 5. 关键决策

- **首页首屏只预取“基础信号”** —— 代码现实是 splash 阶段只取课表与查寝，不把跑步 / 劳教也塞进阻塞链。来源：`MainActivity.kt:35-43`、`feature/home/domain/HomeBootstrap.kt:54-68`。这条决定直接影响后续是否把新摘要数据加入 `HomeBootstrap`。
- **首页叙事规则以课程节次表为准** —— 场景时间判断统一依赖 `SectionSchedule`，而不是零散写死在 UI。来源：`feature/home/domain/SceneResolver.kt:59-127`、`feature/home/domain/SectionSchedule.kt:13-35`。
- **架构文档以代码现实为准，设计意图仅作对照输入保留** —— `homepage-design-spec.md` 预期”单智慧卡片状态机”，当前代码只落地了场景优先级、切换触发与部分动画约束。来源：`docs/项目规划/homepage-design-spec.md:81-99`、`docs/项目规划/homepage-design-spec.md:126-136`、`feature/home/ui/HomeDashboard.kt:114-200`。

## 6. 代码锚点

- `MainActivity.kt:28-43` — splash 放行与 `HomeBootstrap` 启动入口
- `feature/home/domain/HomeBootstrap.kt:44-72` — 冷启动预加载主链
- `feature/home/ui/HomeViewModel.kt:68-217` — 首页状态聚合、刷新与副标题计算
- `feature/home/domain/SceneResolver.kt:41-127` — 场景优先级与时间规则
- `feature/home/domain/SectionSchedule.kt:13-35` — 固定节次时刻表
- `feature/home/domain/SubtitleLibrary.kt:32-175` — 通用副标题桶与场景文案
- `feature/home/ui/HomeDashboard.kt:87-200` — 首页主渲染骨架
- `feature/home/ui/HomeDashboard.kt:208-335` — 问候动画与下一节 headline
- `feature/home/ui/HomeScreen.kt:51-146` — 三 tab 容器与“我的”页路由出口

## 7. 已知约束 / 边界情况

- **劳教数据当前被加载但不在首页渲染** —— `HomeViewModel` 会把 `laborHours` 填进 `HomeDashboardData`，但 `HomeDashboard` 当前没有对应卡片。锚点：`feature/home/ui/HomeViewModel.kt:140-161` / `feature/home/ui/HomeDashboard.kt:167-200`。
- **主页“空态居中问候”尚未按设计稿落地** —— 当前无论有无场景，页面都仍保留分割线、headline、课程卡、跑步卡骨架。不要假设现有 UI 已具备设计稿中的双态布局。锚点：`docs/项目规划/homepage-design-spec.md:20-58` / `feature/home/ui/HomeDashboard.kt:114-200`。
- **场景判定只依赖课程与查寝，不依赖跑步 / 劳教** —— 跑步与劳教会影响卡片区数据，但不会进入 `SceneResolver`。锚点：`feature/home/ui/HomeViewModel.kt:203-217` / `feature/home/domain/SceneResolver.kt:41-57`。
- **分钟级 headline 当前不是实时钟驱动** —— `NextClassHeadline` 内部用 `remember { LocalTime.now() }` 取一次当前时刻，只有在组合重组时才更新；它不共享 `recomputeSubtitle()` 的每分钟 ticker 输入。锚点：`feature/home/ui/HomeDashboard.kt:282-285` / `feature/home/ui/HomeDashboard.kt:101-107`。

## 8. 相关文档

- 网络层：`codestable/architecture/network-overview.md`
- 跑步模块：`codestable/architecture/running-overview.md`
- 节假日离返校登记搭载在首页：`HomeViewModel.loadExtras()` 中按需加载首个未登记节假日，登记窗口内显示 `HolidayCard`。详见 `codestable/features/2026-04-28-holiday-leave-register/holiday-leave-register-design.md`
- 架构总入口：`codestable/architecture/DESIGN.md`
- 首页历史设计输入：`docs/项目规划/homepage-design-spec.md`
