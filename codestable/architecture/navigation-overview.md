---
doc_type: architecture
slug: navigation-overview
scope: navigation/ 包的路由定义、导航图结构、起始页决策、页面转场动画、与跨页面 ViewModel 共享模式
summary: navigation 以 Routes 常量 + NavGraph 集中管理 14 条路由，起始页由 isOnboarded / isLoggedIn 两态 Flow 合并决定，支持页面间 SavedStateHandle 传参与跨页面 ViewModel 共享
status: current
last_reviewed: 2026-04-22
tags: [navigation, navgraph, routes, viewmodel-sharing, transition]
depends_on: [home-overview, login-overview, checkin-overview, running-overview, labor-overview, aiclass-overview, about-overview]
---

# 导航模块总览

## 0. 术语

- **Routes** — `Routes.kt` 中定义的字符串常量与参数化路由函数；所有路由集中在单文件管理
- **NavGraph** — `NavGraph.kt` 中的 `LightXinNavHost`，集中注册所有 composable 路由
- **起始页决策** — 通过合并 `sessionManager.isOnboarded` 与 `sessionManager.isLoggedIn` 两个 Flow 决定 App 启动时显示 onboarding / login / home 三页之一
- **跨页面 ViewModel 共享** — 运行相关页面（running 四条路由）通过 `hiltViewModel(navController.getBackStackEntry(Routes.HOME))` 共享同一个 ViewModel 实例

## 1. 定位与受众

本 doc 描述 `navigation/` 包的职责边界：

- 所有路由路径的定义与导航图结构
- 起始页决策逻辑与页面转场动画
- 跨页面 ViewModel 共享模式与页面间数据传递方式

适用读者：

- 要加新路由、改导航结构、或调转场动画的人
- 要理解"为什么 running 页面共享 ViewModel"或"查寝详情页如何通知列表页刷新"的人
- 要排查导航返回栈问题或 ViewModel 生命周期异常的人

## 2. 结构与交互

### 2.1 Routes 常量定义

锚点：`navigation/Routes.kt:5-40`

所有路由集中在单文件，按功能分组：

| 组 | 路由数 | 路由 |
|---|---|---|
| 核心 | 3 | ONBOARDING / LOGIN / HOME |
| 查寝 | 2 | CHECKIN_LIST / CHECKIN_DETAIL（含参数 `taskDateId`） |
| 跑步 | 8 | RUNNING_HOME / RUNNING_ACTIVE / RUNNING_SIM / RUNNING_RESULT / RUNNING_ROUTE_SETTINGS / RUNNING_ROUTE_RECORD / RUNNING_ROUTE_LIST / RUNNING_ROUTE_DETAIL（含参数 `templateId`） |
| 劳教 | 2 | LABOR_SUMMARY / LABOR_DETAIL（含参数 `id` / `type`） |
| AI 课堂 | 2 | AICLASS_HOME / AICLASS_SCAN |
| 关于 | 1 | ABOUT |

合计 **18 条路由**（DESIGN.md 记录的 14 条为旧数字，实际已增至 18 条）。

参数化路由使用 `fun` 构造完整路径字符串：
```kotlin
fun checkinDetail(taskDateId: String) = "checkin/detail/$taskDateId"
fun runningRouteDetail(templateId: String) = "running/route/detail/$templateId"
fun laborDetail(id: String, type: String) = "labor/detail/$id/$type"
```

### 2.2 起始页决策

锚点：`navigation/NavGraph.kt:56-64`

`startStateFlow` 合并 `sessionManager.isOnboarded` 与 `sessionManager.isLoggedIn` 两个 Flow：

```
!onboarded           → ONBOARDING
onboarded && !loggedIn → LOGIN
onboarded && loggedIn  → HOME
```

决策逻辑在 Compose 层通过 `collectAsState(initial = null)` 订阅，初始值为 null 时显示 `LxLoading` 占位。这种设计避免了启动时的白屏闪烁——在 Flow 发出第一个值之前，用户看到的是加载指示器而非错误页面。

### 2.3 页面转场动画

锚点：`navigation/NavGraph.kt:77-88`

全局默认转场：
- **进入**：`fadeIn(300ms) + slideInHorizontally(300ms) { it / 4 }`（从右滑入 1/4 屏幕）
- **退出**：`fadeOut(200ms)`
- **返回进入**：`fadeIn(300ms) + slideInHorizontally(300ms) { -it / 4 }`（从左滑入 1/4 屏幕）
- **返回退出**：`fadeOut(200ms) + slideOutHorizontally(200ms) { it / 4 }`（向右滑出）

部分页面覆盖默认转场：
- ONBOARDING：`fadeIn(400ms)` / `fadeOut(300ms)` — 无滑动
- LOGIN：`fadeIn(400ms)` / `fadeOut(300ms)` — 无滑动
- HOME：`fadeIn(400ms)` / `fadeOut(200ms)` — 无滑动

这种设计让"深层页面"有推入推出感，而"一级页面"（onboarding / login / home）只有淡入淡出。

### 2.4 跨页面 ViewModel 共享

锚点：`navigation/NavGraph.kt:170-237`

跑步模块的四个页面（RUNNING_HOME / RUNNING_ACTIVE / RUNNING_SIM / RUNNING_RESULT）通过 `hiltViewModel(navController.getBackStackEntry(Routes.HOME))` 共享同一个 `RunningViewModel` 实例。这个 ViewModel 的生命周期绑定到 HOME 路由的 BackStackEntry，而非各个跑步页面自身。

同理，路线模板的三个页面（RUNNING_ROUTE_SETTINGS / RUNNING_ROUTE_RECORD / RUNNING_ROUTE_LIST）共享 `RouteTemplateViewModel`，绑定到 RUNNING_ROUTE_SETTINGS 的 BackStackEntry。

AI 课堂的两个页面（AICLASS_HOME / AICLASS_SCAN）共享 `AiClassViewModel`，绑定到 AICLASS_HOME。

这种模式避免了 ViewModel 在页面跳转时重建，让跑步过程中的状态（计时、轨迹等）在页面切换时保持。

### 2.5 页面间数据传递

锚点：`navigation/NavGraph.kt:140-167`

查寝列表与详情页之间通过 `SavedStateHandle` 传递"是否需要刷新"信号：

1. 详情页提交成功时：`navController.previousBackStackEntry?.savedStateHandle?.set("checkin_refresh", true)`
2. 列表页通过 `backStackEntry.savedStateHandle.getStateFlow("checkin_refresh", false)` 订阅
3. 消费后置回 false

这是 Jetpack Navigation 推荐的"页面间通信"模式，避免了 ViewModel 级别的耦合。

### 2.6 导航与登录/退出的关系

- **登录成功**：`navigate(HOME) { popUpTo(LOGIN) { inclusive = true } }` — 移除登录页
- **退出登录**：由 `SessionManager.clearSession()` 触发 → `sessionManager.isLoggedIn` Flow 变为 false → `startStateFlow` 重新计算 → 自动跳转到 LOGIN
- **onboarding 确认**：`navigate(LOGIN) { popUpTo(ONBOARDING) { inclusive = true } }` — 移除欢迎页

## 3. 数据与状态

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `Routes` 常量 | 所有路由的字符串定义 | `Routes.kt` |
| `NavHostController` | 导航控制器，由 `rememberNavController()` 创建 | `NavGraph.kt` |
| `startRoute` | 当前起始页，由两态 Flow 合并计算 | `NavGraph.kt:56-64` |
| `SavedStateHandle` | 页面间键值对传递 | NavGraph 各 composable |

## 4. 关键决策

- **单文件管理所有路由** —— `Routes.kt` 和 `NavGraph.kt` 各一份，不拆分到 feature 包内。虽然违反"按 feature 分包"原则，但导航需要全局视角，分散后不利于排查路由冲突。
- **跨页面 ViewModel 共享而非 Navigation arguments** —— 跑步模块的复杂状态（计时器、轨迹）不适合序列化为路由参数，选择 ViewModel 共享。来源：`NavGraph.kt:170-237`。
- **起始页决策用 Flow 合并而非一次性判断** —— 支持登录态变化后自动重定向。来源：`NavGraph.kt:56-64`。
- **无嵌套 NavGraph** —— 所有路由平铺在同一个 NavHost 中，没有嵌套导航图。当前路由数量（18 条）尚未达到需要拆分的复杂度。
- **转场动画统一管理** —— 在 NavHost 层面设置默认动画，各页面按需覆盖。避免在每个 composable 里重复写 enterTransition/exitTransition。

## 5. 代码锚点

- `navigation/Routes.kt:5-40` — 全部 18 条路由定义
- `navigation/NavGraph.kt:56-64` — 起始页决策逻辑
- `navigation/NavGraph.kt:77-88` — 默认转场动画
- `navigation/NavGraph.kt:90-115` — OnboardingScreen 路由
- `navigation/NavGraph.kt:117-129` — LoginScreen 路由
- `navigation/NavGraph.kt:131-137` — HomeScreen 路由
- `navigation/NavGraph.kt:140-167` — Checkin 路由 + SavedStateHandle 通信
- `navigation/NavGraph.kt:169-237` — Running 路由 + ViewModel 共享
- `navigation/NavGraph.kt:239-292` — Route Simulation 路由 + ViewModel 共享
- `navigation/NavGraph.kt:294-315` — Labor 路由
- `navigation/NavGraph.kt:317-341` — AI Class 路由 + ViewModel 共享
- `navigation/NavGraph.kt:343-346` — About 路由

## 6. 已知约束 / 边界情况

- **RunningViewModel 绑定到 HOME 的 BackStackEntry** —— 这意味着即使所有跑步页面都已弹出，HOME 页面存在时 RunningViewModel 仍然存活。只有当 HOME 页面也被弹出时（即退出登录），RunningViewModel 才会销毁。
- **路线模板的 ViewModel 绑定到 RUNNING_ROUTE_SETTINGS** —— 如果用户直接导航到 ROUTE_LIST 或 ROUTE_DETAIL（如通过 deep link），而没有经过 SETTINGS 页面，ViewModel 获取会失败。当前没有 deep link 支持，所以这不是问题。
- **CHECKIN_LIST 使用 `getStateFlow` 订阅 refresh 信号** —— 这是 Compose 安全的订阅方式，但需要记得在消费后置回 false，否则下次进入列表页时会错误触发刷新。
- **schedule 路由未在 NavHost 中注册** —— `Routes.SCHEDULE` 已定义但未出现在 `NavGraph.kt` 中，因为课表是 HomeScreen 的 tab 1，由 HomeScreen 内部管理，不经过 NavHost。
- **初始 loading 状态不会在 401 时显示错误** —— `startRoute` 为 null 时只显示 `LxLoading`，没有超时或错误处理。如果 Flow 永不发射值（如 DataStore 损坏），用户会卡在加载页。

## 7. 相关文档

- 首页三 tab 容器：`easysdd/architecture/home-overview.md` §2.1
- 登录导航：`easysdd/architecture/login-overview.md` §2.3
- 查寝详情页通信：`easysdd/architecture/checkin-overview.md` §2.4
- 跑步模块 ViewModel 共享：`easysdd/architecture/running-overview.md`
- 架构总入口：`easysdd/architecture/DESIGN.md`
