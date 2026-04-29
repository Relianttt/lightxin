---
doc_type: architecture
slug: onboarding-overview
scope: feature/onboarding/ 的首启欢迎页：Canvas 插画动效、分步入场动画、确认/退出流程
summary: onboarding 是纯 UI 模块，无 data/domain 层，以 Canvas 绘制校园晨景插画（含多组独立动效），配合 staggered 入场动画展示欢迎文案；确认后标记 onboarded 状态并跳转登录页
status: current
last_reviewed: 2026-04-22
tags: [onboarding, welcome, canvas, animation, splash]
depends_on: []
---

# 首启欢迎页模块总览

## 0. 术语

- **Onboarding** — 应用首次启动时的欢迎页面，仅展示一次；确认后通过 `SessionManager.markOnboarded()` 持久化
- **Staggered 入场** — 文案元素按 index 依次延迟出现（80ms + index × 90ms），配合 fadeIn + slideInVertically 动画
- **Canvas 插画** — 用 Compose `Canvas` 绘制的校园晨景插画，viewBox 坐标系 390×280，含太阳/云/建筑/树/人物等多组独立动效

## 1. 定位与受众

本 doc 描述 `feature/onboarding/` 的职责边界：

- 应用首次启动的品牌展示与用户告知
- 无 data 层、无 domain 层、无 ViewModel——是项目中最简单的 feature 模块
- 与 SessionManager 的 `isOnboarded` 状态耦合

适用读者：

- 要改欢迎页文案、动效、或插画的人
- 要理解 onboarading 与登录/主页的导航流程的人
- 要排查"为什么每次启动都看到欢迎页"或"为什么看不到欢迎页"的人

## 2. 结构与交互

### 2.1 单文件结构，无 data/domain 层

```
OnboardingScreen.kt         → UI 布局 + 入场动画 + 按钮
OnboardingIllustration.kt   → Canvas 插画（含全部动效逻辑）
```

没有 Repository、没有 ViewModel、没有领域模型。这是设计上的选择——onboarding 是纯展示页面，不需要网络请求或持久化状态。

`onboarded` 状态的读写由 `SessionManager`（在 `core/auth/` 中）管理，通过 `NavGraph` 的导航流程控制。

### 2.2 导航入口：isOnboarded 守卫

锚点：`navigation/NavGraph.kt:56-64`

`LightXinNavHost` 通过合并 `sessionManager.isOnboarded` 和 `sessionManager.isLoggedIn` 两个 Flow 决定起始页：

```kotlin
when {
    !onboarded -> Routes.ONBOARDING
    !loggedIn -> Routes.LOGIN
    else -> Routes.HOME
}
```

所以 onboarding 是启动链的第一站——只有在标记 onboarded 后，用户才会看到登录页或主页。

### 2.3 OnboardingScreen 的两种退出路径

锚点：`feature/onboarding/ui/OnboardingScreen.kt:36-87`

| 按钮 | 行为 | 导航 |
|---|---|---|
| "我已知晓" | `sessionManager.markOnboarded()` → 跳转登录页 | `popUpTo(ONBOARDING) { inclusive = true }` |
| "暂不进入" | 直接退出 App | `finishAndRemoveTask()`（API 21+）或 `finishAffinity()` |

"暂不进入"的设计是让用户在不接受告知的情况下直接退出应用，不在未确认的情况下进入主页。

### 2.4 Canvas 插画架构

锚点：`feature/onboarding/ui/OnboardingIllustration.kt:54-341`

插画使用 Compose `Canvas` 绘制，viewBox 坐标系 390×280，通过 `scale = size.width / VB_W` 自适应宽度，高度按 aspectRatio 锁定。

**动效分组（7 组独立 `infiniteRepeatable`）：**

| 动效 | 周期 | 说明 |
|---|---|---|
| 晨光呼吸 | 14s | 地平线椭圆光晕透明度 0.06↔0.22 |
| 太阳浮动 | 7s | 整体上下浮动 ±8px |
| 光芒脉动 | 3.5s | 射线透明度 0.3↔0.85 |
| 云飘 | 16s / 11s | 两朵云水平往返 |
| 树摇摆 | 5.8s / 6.4s | 两棵树各自绕根部旋转，错相 |
| 窗户辉光 | 4-6s | 6 组独立错相的窗户透明度 |
| 走路小人 | 18s / 22s | 两个小人物沿路径上行，两端淡入淡出 |

**插画元素层级（从底到顶）：**

1. 天空底 → 2. 晨光椭圆 → 3. 地面 → 4. 小径 → 5. 云 → 6. 太阳组 → 7. 小鸟 → 8. 建筑群（宿舍/主楼/右楼）→ 9. 树木 → 10. 灌木 → 11. 走路小人

### 2.5 文案入场动画

锚点：`feature/onboarding/ui/OnboardingScreen.kt:89-102`

使用 `StaggerItem` 封装 `AnimatedVisibility`，三个元素按 index 依次延迟：
- 标题（`NewsreaderLarge` 衬线字）：index 0
- 描述文案：index 1
- 按钮组：index 2

入场效果：`fadeIn(450ms) + slideInVertically(450ms) { it / 4 }`。

## 3. 数据与状态

onboarding 自身没有可变状态。所有状态是局部的 Composable 状态：

| 状态 | 类型 | 位置 |
|---|---|---|
| `visible`（每个 StaggerItem） | `remember { mutableStateOf(false) }` | 局部，驱动入场动画 |
| `onboarded`（跨页面） | SessionManager 的 DataStore | 由 SessionManager 持久化 |

## 4. 关键决策

- **纯 UI 模块，无 data/domain 层** —— onboarding 不需要网络或持久化，保持最简。来源：`feature/onboarding/ui/` 下只有两个文件。
- **Canvas 绘制插画而非资源图片** —— 避免多密度 PNG 适配，动效通过 Compose Animation 原生控制。来源：`OnboardingIllustration.kt`。
- **infiniteRepeatable 动效而非 LaunchedEffect 循环** —— 使用 `rememberInfiniteTransition` 驱动持续动画，无需手动管理协程生命周期。来源：`OnboardingIllustration.kt:55-127`。
- **viewBox 坐标系 + scale 自适应** —— 固定 390×280 的抽象坐标系，通过 `scale = size.width / VB_W` 映射到实际像素。来源：`OnboardingIllustration.kt:134-135`。
- **"暂不进入"直接退出 App** —— 不在用户未确认的情况下进入主页。来源：`OnboardingScreen.kt:106-113`。

## 5. 代码锚点

- `feature/onboarding/ui/OnboardingScreen.kt:36-87` — 欢迎页布局 + 按钮
- `feature/onboarding/ui/OnboardingScreen.kt:89-102` — StaggerItem 入场动画
- `feature/onboarding/ui/OnboardingIllustration.kt:54-341` — Canvas 插画全量绘制
- `feature/onboarding/ui/OnboardingIllustration.kt:55-127` — 7 组动效定义
- `navigation/NavGraph.kt:56-64` — isOnboarded 守卫决定起始页
- `navigation/NavGraph.kt:90-115` — OnboardingScreen 路由注册

## 6. 已知约束 / 边界情况

- **onboarding 只显示一次** —— 除非清除应用数据或重新安装。没有"重新查看欢迎页"的入口。
- **Canvas 插画不支持深色模式** —— 使用硬编码颜色值（`SkyCream` / `GroundSand` 等），不跟随主题切换。插画设计本身是晨景暖色调，深色模式下可能不协调。
- **"暂不进入"在 Android 5+ 使用 `finishAndRemoveTask()`** —— 从最近任务列表中移除应用。低版本回退到 `finishAffinity()`。来源：`OnboardingScreen.kt:106-113`。
- **标记 onboarded 后才跳转** —— `markOnboarded()` 是挂起函数，通过 `scope.launch` 执行。导航在协程启动后立即触发，不等待 DataStore 写入完成（写入可能异步进行）。
- **无 ViewModel 意味着进程重建后状态丢失** —— 但 welcome 页在进程重建时本就不应该再次显示（除非 DataStore 数据丢失）。

## 7. 相关文档

- SessionManager / isOnboarded 状态管理：`core/auth/SessionManager.kt`
- 导航起始页决策：`navigation/NavGraph.kt`
- 架构总入口：`easysdd/architecture/DESIGN.md`
