---
doc_type: architecture
slug: about-overview
scope: feature/about/ 的关于页品牌展示与调试开关
summary: about 以 AboutViewModel 管理版本号读取与 DeveloperPrefs 调试开关，UI 层含品牌展示卡与确认弹窗保护的调试开关
status: current
last_reviewed: 2026-04-22
tags: [about, version, developer-prefs, debug]
depends_on: [designsystem-overview]
---

# 关于页模块总览

## 0. 术语

- **DeveloperPrefs** — `core/settings/DeveloperPrefs`，调试功能的开关管理器，基于 DataStore 持久化；控制"模拟提交"与"路线模拟"等调试入口的可见性
- **调试开关** — 关于页底部的 Switch 控件，开启后暴露 HomeScreen"我的"页中的调试功能入口

## 1. 定位与受众

本 doc 描述 `feature/about/` 的职责边界：

- 应用品牌展示（Logo / 名称 / 版本号 / 标语）
- 调试功能的启用入口（带确认弹窗保护）
- 不包含任何网络请求或外部数据

适用读者：

- 要改关于页展示内容或调试开关逻辑的人
- 要理解调试功能的启用路径的人

## 2. 结构与交互

### 2.1 单页结构

```
AboutViewModel.kt   → 版本号读取 + DeveloperPrefs 开关
AboutScreen.kt      → 品牌卡 + 调试开关 UI
```

无 data 层、无 domain 层。`DeveloperPrefs` 是 `core/settings/` 的基础设施，不在 feature 内部。

### 2.2 版本号读取

锚点：`feature/about/ui/AboutViewModel.kt:44-49`

`readVersionName()` 通过 `PackageManager.getPackageInfo()` 读取 `versionName`，失败时返回空字符串。在 ViewModel init 时一次性读取，不监听变化。

### 2.3 DeveloperPrefs 订阅

锚点：`feature/about/ui/AboutViewModel.kt:30-35`

ViewModel init 时通过 `developerPrefs.isAdvancedEnabled` 的 Flow 持续监听调试开关状态。UI 层通过 `collectAsState()` 订阅。

切换开关时调用 `setAdvancedEnabled()`，写入 DataStore。

### 2.4 确认弹窗保护

锚点：`feature/about/ui/AboutScreen.kt:83-107`

开启调试功能需要经过 `LxDialog` 确认：
- 确认前：弹出 `LxDialog`（Destructive 色调），说明"开启后将显示模拟提交与路线模拟入口"
- 确认后：调用 `viewModel.setAdvancedEnabled(true)`
- 取消：不改变状态

关闭调试功能不需要确认，直接关闭。

### 2.5 BrandCard

锚点：`feature/about/ui/AboutScreen.kt:110-158`

品牌展示卡包含：
- `splash_logo` 图标（1.45x 缩放）
- "轻小信" 标题（`NewsreaderDisplay` 衬线字，`LxTerra` 色）
- "让校园生活更轻盈" 标语
- 版本号（v${versionName}）

## 3. 数据与状态

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `AboutUiState` | versionName / advancedEnabled | `AboutViewModel.kt:16-19` |
| `isAdvancedEnabled` | DataStore Flow，调试开关持久化状态 | `DeveloperPrefs` |

## 4. 关键决策

- **调试开关放在关于页而非设置页** —— 项目没有独立的设置页面；关于页是最自然的"不常用但需要时能找到"的位置。
- **确认弹窗使用 Destructive 色调** —— 强调这是一个"对普通用户有风险"的操作。来源：`AboutScreen.kt:105`。
- **关闭不需要确认** —— 从开发调试状态回到正常状态应该低摩擦。来源：`AboutScreen.kt:86-88`。

## 5. 代码锚点

- `feature/about/ui/AboutViewModel.kt:22-51` — 版本号 + DeveloperPrefs
- `feature/about/ui/AboutScreen.kt:54-107` — 品牌卡 + 调试开关 + 确认弹窗
- `feature/about/ui/AboutScreen.kt:110-158` — BrandCard 组件
- `feature/about/ui/AboutScreen.kt:161-192` — DeveloperToggleCard 组件
- `core/settings/DeveloperPrefs.kt` — 调试开关持久化

## 6. 已知约束 / 边界情况

- **调试开关只控制 UI 可见性，不控制功能执行** —— 即使开关关闭，已通过代码直接调用的调试功能仍可执行。开关只是隐藏了入口。
- **版本号读取在 init 时一次性完成** —— 如果应用在运行时更新（如 Play Core 的即时更新），版本号不会自动刷新。
- **版本号读取失败时显示空字符串** —— 不影响页面其他内容的展示。

## 7. 相关文档

- 设计系统品牌展示：`codestable/architecture/designsystem-overview.md`
- 架构总入口：`codestable/architecture/DESIGN.md`
