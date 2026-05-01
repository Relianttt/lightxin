---
doc_type: architecture
slug: designsystem-overview
scope: core/designsystem/ 下的 Token 层（Color / Type / Shape）、LightXinTheme 入口、Light/Dark 切换机制，以及 11 个通用组件文件的形态与使用约束
summary: token 分"Raw 字面量 + 语义 composable"两层，语义层用 isSystemInDarkTheme() 在 Light/Dark 自动切换；组件全部绕开 Material3 默认 ripple、自管 interactionSource；Compose Dark 与资源层 forceDarkAllowed=false 是两层正交机制
status: current
last_reviewed: 2026-04-21
tags: [designsystem, theme, color, typography, components, dark-mode]
depends_on: []
---

# 设计系统总览

## 0. 术语

- **Raw token** — `Color.kt` 里 `internal val Lx*Raw = Color(0xFFxxxxxx)` 的字面量常量，不跟随主题；仅供 `Theme.kt` 构造 `ColorScheme` 使用
- **语义 token** — 外层 `val Lx*: Color @Composable @ReadOnlyComposable get() = ...`，跟随 `isSystemInDarkTheme()` 自动切换；UI 层只导入这一层
- **光学尺寸字族** — Newsreader 按 14pt / 24pt / 36pt 提供三档光学变体，小字更粗、大字字距更紧；分别对应 `NewsreaderSmall / Large / Display`
- **陶土色 / Terra** — `LxTerra`（Light `#C4704B` / Dark `#E59975`），项目主题色；使用密度有准则（§2.7 / §6）
- **历史别名** — `LxSuccess` / `LxWarning` / `LxError` 三个 composable 直接指向 `LxSage` / `LxAmber` / `LxRose`，未在 UI 层严格按"语义色"迁移（§6 观察项）

## 1. 定位与受众

本 doc 覆盖 `core/designsystem/` 两个子包：

- `theme/` — Color / Type / Shape + `LightXinTheme` 入口
- `component/` — 11 个 `Lx*` 通用组件文件

读者：

- 要加 / 改通用组件，或判断"新组件是否该进 designsystem"的人
- 要改色板 / 字体 / 圆角 token，或判断"这里能不能用 `LxTerra`"的人
- 要排查"为什么深色模式下某处颜色不对 / 中文字体没切"的人
- feature-design 阶段对齐"我这块 UI 有没有现成组件可复用"的人

读完能做到：

- 知道从原始色到 UI 导入面之间走了几层、为什么是这几层
- 知道 Light / Dark 在 Compose 层和资源层各由什么机制承载、彼此是否共享
- 知道 11 个组件各自的形态与适用场景，避免重复造
- 知道陶土色 / 衬线字 / 圆角档位这几组"像审美选择"的东西背后的硬约束

## 2. 结构与交互

### 2.1 两层 color token：Raw 字面量 → 语义 composable

token 故意分两层：

- **Raw 层**（`internal val Lx*Raw`）— 纯字面量、不跟随主题；供 `Theme.kt` 组 `LightColorScheme` / `DarkColorScheme` 消费
- **语义层**（`val Lx*: Color @Composable @ReadOnlyComposable get()`）— UI 层的实际导入面，在 `@Composable` 作用域里 `if (isSystemInDarkTheme())` 二选一

锚点：`core/designsystem/theme/Color.kt:14-92`（Raw）/ `Color.kt:99-164`（语义）。

这样分的目的：

- `ColorScheme` 要的是静态 `Color` 实例（Material3 API 约束），必须是 Raw
- UI 层要的是"随主题自动换"的 token，必须进 Composable
- Raw 的 `internal` 可见性强制禁止 UI 层直接导入，避免误用字面量丢失主题切换

### 2.2 三组语义 token 按用途分家

| 组 | 代表 token | 用途 |
|---|---|---|
| 羊皮纸背景 | `LxParchment` / `LxCream` / `LxCard` / `LxSand` / `LxSandDeep` / `LxCardBorder` | 页面底 / 卡片底 / 填充块 / 边框 |
| 文本阶梯 | `LxInk` / `LxInkSoft` / `LxInkMuted` / `LxInkFaint` / `LxInkGhost` | 主文字 / 次标题 / 辅助文案 / 占位 / 幽灵 |
| 品牌语义色 | `LxTerra` / `LxSage` / `LxAmber` / `LxPlum` / `LxSlate` / `LxRose` + 各自 `*Soft` | 主题色 + 状态色，`*Soft` 版用于背景 / 填充 |

锚点：`Color.kt:99-154`。

另外两条横切：

- `LxCategoryColors` — 8 色分类色板（课表卡片、劳动图表）；Light / Dark 各一份（`Color.kt:40-49,83-92,162-164`）
- 历史别名 `LxSuccess` / `LxWarning` / `LxError` — 直接别名到 `LxSage` / `LxAmber` / `LxRose`（`Color.kt:157-159`），语义层本想分开、实际仍沿用品牌色

### 2.3 `LightXinTheme` 是唯一入口，承载 Light + Dark 两套 ColorScheme

`MainActivity.onCreate()` 只调用一次 `LightXinTheme {}`（不传参数，默认 `darkTheme = isSystemInDarkTheme()`）。Theme 内部二选一 `LightColorScheme` / `DarkColorScheme`，再与 `LightXinTypography` / `LightXinShapes` 组成 `MaterialTheme`。

锚点：`MainActivity.kt:62-66` / `core/designsystem/theme/Theme.kt:64-77`。

两套 ColorScheme 都是完整 Material3 映射（`primary` / `surface` / `error` / `outline` 等字段全填），`DarkColorScheme` 专用一组 `LxDark*Raw` token（`Theme.kt:37-62`）。**Dark 主题在 Compose 层是完整落地的**，会跟随系统 dark mode 自动切换。

### 2.4 系统级反色滤镜独立关闭

资源层两套主题都显式声明 `android:forceDarkAllowed=false`：

- `res/values/themes.xml:7` — Light 资源主题（父主题 `android:Theme.Material.Light.NoActionBar`）
- `res/values-night/themes.xml:6` — Dark 资源主题（父主题 `android:Theme.Material.NoActionBar`）

这**不是**为了禁用 Compose Dark，而是拦截厂商（MIUI / HyperOS 等）对 Light 应用强加的反色滤镜。暖色 token 一旦被反色就失去设计意图（`LxParchment` 羊皮纸会变成深蓝黑），所以在资源层关掉这条路。

Compose Dark Scheme 与资源层 `forceDarkAllowed` 是**两层正交机制**——前者是应用自己的视觉语言切换，后者是"防系统插手"。同时打开 MIUI 全局深色 + 系统 Dark 模式时，应用内会走 Compose Dark，而不是被滤镜强改的 Light。

此外，`MainActivity` 根据 `Configuration.UI_MODE_NIGHT_YES` 决定 `SystemBarStyle.dark` / `light` 以切换状态栏 / 导航栏图标明暗（`MainActivity.kt:45-57`），与 Compose Theme 协同而非重复。

### 2.5 `LightXinTypography` 三字族策略

字体按尺寸切换成四条路径：

| 用途 | 字族 | 尺寸范围 |
|---|---|---|
| Display / Headline Large | `NewsreaderDisplay`（36pt 光学尺寸） | 30sp+ |
| Headline Medium / Title Large / Medium | `NewsreaderLarge`（24pt 光学尺寸） | 20–22sp |
| Title Small | `NewsreaderSmall`（14pt 光学尺寸） | 14–18sp |
| Body / Label / 数字 | `Outfit` | 11–15sp |

锚点：`core/designsystem/theme/Type.kt:20-55`（字族）/ `Type.kt:56-147`（Typography 映射）。

关键约束（中文 fallback 机制）：

- Newsreader 只覆盖拉丁字形，中文字符会 fallback；三个 Newsreader 字族的 `FontFamily` 链都追加了 `Noto Serif SC Regular`（`Type.kt:13-14,20-45`）
- Noto Serif SC **只用 Regular 字重**；同一个 Regular TTF 注册为 `FontWeight.Normal` + `FontWeight.Medium` 两条 Font 项，让 Medium 场景也 fallback 到 Regular 中文，避免中文"黑一块"

另提供一个独立 `TextStyle`：`LxTabularNums`（`Type.kt:154`）—— 启用 OpenType `tnum` feature 做等宽数字，跑步 / 劳教工时等数字密集场景按需 `.merge()` 使用。

### 2.6 圆角四档 + Material3 Shapes 映射

```
RSm   = 8dp   → 小图标容器 / 分类小标签       → shapes.extraSmall
RMd   = 12dp  → 输入框 / 中型卡片              → shapes.small
RLg   = 16dp  → 主卡片 / 大按钮                → shapes.medium / shapes.large
RPill = 20dp  → 胶囊（Badge / 周选择器 chip）   → shapes.extraLarge
```

锚点：`core/designsystem/theme/Shape.kt:8-19`。

`shapes.medium` 与 `shapes.large` 都映射到 `RLg`（16dp），Material3 提供的 large 在本项目没有差异化（§6 观察项）。UI 侧一般直接 import `RSm / RMd / RLg / RPill` 常量，而非走 `MaterialTheme.shapes.*`。

### 2.7 11 个通用组件文件

| 文件 | 导出的 Composable | 关键细节 |
|---|---|---|
| `LxCard.kt` | `LxCard` | 16dp 圆角、`LxCard` 底、0dp 阴影；可点击时按压切 `LxCream` |
| `LxButton.kt` | `LxButton` / `LxSecondaryButton` / `LxOutlinedButton` / `LxTextButton` | 四级层级：陶土实心 / 沙填充 / 沙描边 / 陶土文字；前三者固定 52dp 高 |
| `LxIconButton.kt` | `LxIconButton` | 40dp 圆形、透明底、按压切 `LxTerraSoft` |
| `LxFloatingActionButton.kt` | `LxFloatingActionButton` | 56dp 圆形 + 10dp 阴影、陶土实心 |
| `LxChoiceChip.kt` | `LxChoiceChip` | 20dp 胶囊、最小 36dp 高；选中态描边切 `LxTerra`、底切 `LxTerraSoft` |
| `LxTextField.kt` | `LxTextField` | 56dp 高（单行）、`LxCream` 底、标签上置不走 Material3 浮动 label |
| `LxDialog.kt` | `LxDialog` + `LxDialogConfirmTone` | 基于 `LxCard` 的自定义 Dialog；`Destructive` 色走 `LxRose`，`Primary` 走 `LxTerra` |
| `LxTopBar.kt` | `LxTopBar` | 60dp 高、中心标题 + 可选返回；自带 `statusBarsPadding`、`LxParchment` 底 |
| `LxDetailRow.kt` | `LxDetailRow` | 72dp 标签列 + 右值；value 为空直接 `return` |
| `LxLoadingState.kt` | `LxProgressIndicator` / `LxLoading` / `LxEmpty` / `LxError` / `LxShimmerCard` | 加载 / 空态 / 错误 / 骨架的统一出口 |
| `LxAnimatedNumber.kt` | `LxAnimatedNumber` | Float 插值 + 800ms tween；跑步里程等数字计量场景 |

所有可点击组件（Card / Button / IconButton / FAB / ChoiceChip / TextButton）都**绕开 Material3 默认 ripple**，用 `indication = null` + 自管 `MutableInteractionSource` 做 pressed 色切换——这是项目选择"暖色质感 + 自控按压"路线的直接体现。

组件的共同模式：

- `Modifier` 是第一个可选参数（`modifier: Modifier = Modifier`）
- 颜色值**全部**来自 `theme/Color.kt` 语义层，组件内部不写字面量色
- `shape` 优先走 `MaterialTheme.shapes.*`，特殊场景再用 `RoundedCornerShape(XXdp)`

## 3. 数据与状态

designsystem 自身**没有可变状态**——token 是常量 / 单次读取，组件都是无状态 `@Composable`，状态由调用方持有。

唯一的局部状态是六个组件各自内部的 `MutableInteractionSource` + `isPressed`，用于按压态色切换：

- `LxCard`（可点击分支）/ `LxButton` 族 / `LxIconButton` / `LxFloatingActionButton` / `LxChoiceChip` / `LxTextButton`

这些 interaction 状态只影响当前组件视觉，不对外暴露、不持久化。

## 4. 关键决策

本节引用已归档的 decision 文档，每条一两行结论 + 链接。

- **暖色羊皮纸视觉语言（Phase 9A）** —— 把项目从"Material 蓝橙"改为陶土 / 沙 / 暖墨的羊皮纸质感。引用：`compound/2026-04-22-decision-warm-parchment-visual-language.md`
- **Newsreader + Outfit + Noto Serif SC 三字族与中文 fallback 策略（Phase 9C）** —— 中文 fallback 只用 Regular，同一 TTF 注册为 Normal + Medium 两条 Font 项。引用：`compound/2026-04-22-decision-triple-typeface-cjk-fallback.md`
- **陶土色使用密度准则** —— 一屏最多 2 次；实心陶土 = 主 CTA、轮廓陶土 = 次级 / destructive；Badge / Icon 不用陶土。引用：`compound/2026-04-22-decision-terra-color-usage-density.md`
- **底栏图标线性化（Phase 9B / 9C）** —— Tab bar 统一 `Icons.Outlined.*`，靠陶土色 + `FontWeight.SemiBold` 区分选中，不切 `Icons.Filled.*`。来源：`docs/项目规划/UI改造指南.md:341`。这条落在 feature 层（首页底栏），不在 `core/designsystem/` 内，但和设计系统审美一致。
- **组件绕开 Material3 默认 ripple，自管 `interactionSource`** —— 所有可点击组件用 `indication = null` + 自己的 `isPressed` 切色。来源：代码现状反推；统一出现在 `LxButton.kt:138-140` / `LxCard.kt:26-39` 等处。未正式归档。

## 5. 代码锚点

- `core/designsystem/theme/Color.kt:14-92` — Raw 字面量层（Light + Dark 两套）
- `core/designsystem/theme/Color.kt:99-164` — 语义 composable token 层
- `core/designsystem/theme/Color.kt:157-159` — 历史别名 `LxSuccess` / `LxWarning` / `LxError`
- `core/designsystem/theme/Theme.kt:10-62` — `LightColorScheme` / `DarkColorScheme`
- `core/designsystem/theme/Theme.kt:64-77` — `LightXinTheme` 入口
- `core/designsystem/theme/Type.kt:20-55` — 四字族（Newsreader Small / Large / Display + Outfit）
- `core/designsystem/theme/Type.kt:56-147` — `LightXinTypography` 完整映射
- `core/designsystem/theme/Type.kt:154` — `LxTabularNums` 等宽数字 feature
- `core/designsystem/theme/Shape.kt:8-19` — 四档圆角常量 + `LightXinShapes`
- `core/designsystem/component/` — 11 个组件文件逐个
- `MainActivity.kt:45-57` — 状态栏 / 导航栏明暗切换
- `MainActivity.kt:62-66` — 唯一 `LightXinTheme` 挂载点
- `res/values/themes.xml:2-8` / `res/values-night/themes.xml:2-7` — 资源主题 + `forceDarkAllowed=false`

## 6. 已知约束 / 边界情况

面向实施者（新加组件、改 token、排查主题问题）的约束清单：

- **Raw token 不允许进 UI 层** —— 由 `internal val *Raw` 的可见性保证；组件内部若出现硬编码 `Color(0xFFxxxxxx)`，要么迁到 `Color.kt` 做成语义 token、要么改用已有 token。来源：`Color.kt:14`（`internal` 修饰符注释）。
- **新加组件禁止从 Material3 直接 import 颜色** —— 统一走 `theme/Color.kt` 语义层，否则主题切换失效 / 视觉割裂。来源：现有 11 个组件一致做法。
- **`LxTextField` 不走 Material3 浮动 label** —— 原因：浮动 label 在有描边的情境下会被裁切、和陶土焦点色冲突。替代方案：`Column { Label; BasicTextField }`，label 置上。锚点：`LxTextField.kt:42-126`。
- **中文字体 fallback 必须接 `Noto Serif SC Regular`** —— 否则中文会 fallback 到系统黑体，与设计意图冲突。来源：`docs/项目规划/UI改造指南.md:271-274`。
- **Noto Serif SC 不上 Medium 字重** —— 同一 Regular TTF 注册为 Normal + Medium 两条 Font 项。锚点：`Type.kt:13-14`。
- **陶土色（`LxTerra`）一屏最多 2 次、Badge / Icon 不用** —— 使用密度约束。来源：`docs/项目规划/UI改造指南.md:296-300`。新建 UI 时要自查。
- **Dark 主题会跟随系统 dark mode 自动生效** —— 暖色语义 token 在 Dark 模式下自动换到 `LxDark*Raw`（提亮的陶土 / 提亮的沙色），调用方无需感知。
- **`forceDarkAllowed=false` 只防厂商反色，不控制 Compose Dark** —— 这两层独立；不要把它理解为"强制 Light"。
- **观察项：DESIGN.md §4 描述偏差** —— 总入口当前写"强制 Light 主题（Phase 9B）"，但代码层 Compose Dark 已完整落地并跟随系统。下次更新 `DESIGN.md` 时应同步修正这一条描述。
- **观察项：`LxSuccess` / `LxWarning` / `LxError` 仅做别名** —— 三条语义色直接指向 `LxSage` / `LxAmber` / `LxRose`（`Color.kt:157-159`），UI 层未严格收敛（如 `LxDialog` Destructive 直接用 `LxRose`，没走 `LxError`）。是否统一语义色留待下次决策。
- **观察项：`shapes.medium` 与 `shapes.large` 同为 `RLg`（16dp）** —— Material3 提供的 large 在本项目没有差异化。未来需要"更大"圆角（如全屏 BottomSheet）时再考虑分档。

## 7. 相关文档

- 架构总入口：`codestable/architecture/DESIGN.md`
- 历史设计输入（多数已实现）：`docs/项目规划/UI改造指南.md`（Phase 9A / 9B / 9C 完整决策 + 未决问题 + 组件改造清单）
- 原型参考：`prototype/anthropic-redesign.html`（色板 CSS 变量与第 1 节 token 一一对应）
- 使用 designsystem 的代表性模块：
  - `codestable/architecture/home-overview.md` — 首页叙事骨架消费 `LxCard` / 问候区衬线字等
  - `codestable/architecture/running-overview.md` — 跑步仪表盘 / 设置页大量使用 `LxButton` / `LxTextField` / `LxChoiceChip`
