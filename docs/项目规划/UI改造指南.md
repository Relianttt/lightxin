# UI 改造指南（Anthropic 暖色系重设计）

> 目标：把当前 Material 蓝橙风改造成 `prototype/anthropic-redesign.html` 所定义的暖色、羊皮纸质感、serif + sans 双字族的视觉语言。
>
> 原型文件（定稿）：`prototype/anthropic-redesign.html`
>
> 本指南同时沉淀设计评审过程中明确的细节决策，避免二次返工。

## 实施状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 9A (P0+P1) | 暖色 Token + 登录/欢迎/首页（初版） | ✅ 已落地（commit `ba8b2b6`） |
| Phase 9B (P2) | 课表三态周次 + 我的页合并卡 + 强制 Light + 图标去底色 | ✅ 已落地（commit `d9c9f41`） |
| Phase 9C 精炼 | 字体 fallback + 首页叙事架构 + 我的页精简 + Tab bar 线性化 | ✅ 已落地（详见 §9） |

---

## 0. 执行顺序建议

1. **设计 Token 层**（色板 + 字体 + 圆角 + 间距）—— 影响面最大，必须先做
2. **通用组件层**（`LxCard` / `LxButton` / `LxTextField` / `LxTopBar`）—— 承载 Token，变更后页面自动继承
3. **页面层** 按 登录 → 欢迎（新增）→ 首页 → 课表 → 我的 顺序改造
4. **图标替换** ⚠️ **由用户本人在实现 UI 时统一替换**，本次改造不动任何 `res/drawable` 或 Material Icons 引用；占位照旧，只改外观容器（底色块、圆角）

---

## 1. 设计 Token ✅（Phase 9A 已落地）

### 1.1 色板（`core/designsystem/theme/Color.kt` 全量替换）

| 当前 | 原型目标 | 十六进制 | 语义 |
|---|---|---|---|
| `LxBackground #FAFAF8` | `parchment` | `#F5F0E8` | 主背景（羊皮纸） |
| — | `cream` | `#FAF7F2` | 次级背景、插画框底 |
| `LxSurface #FFFFFF` | `card` | `#FFFDF9` | 卡片底色（偏暖） |
| `LxSurfaceVariant #F5F5F3` | `sand` | `#EDE7DB` | 分隔/浅色填充 |
| — | `sand-deep` | `#DDD5C5` | 建筑/深色填充 |
| `LxOnBackground #1A1A1A` | `ink` | `#2D2A26` | 主文本 |
| `LxOnSurfaceVariant #6B6B6B` | `ink-soft / ink-muted / ink-faint / ink-ghost` | `#4A4640 / #8A847A / #B5AFA5 / #D4CFC6` | 四级文本阶梯 |
| `LxPrimary #5B7FD3`（蓝） | `terra` | `#C4704B` | **主品牌色** |
| — | `terra-soft / terra-glow` | `rgba(196,112,75,.12)` / `.06` | terra 软态 |
| `LxSuccess #4CAF7A` | `sage` | `#6B8F71` | 运动/成功 |
| `LxWarning #E0A145` | `amber` | `#C49A4B` | 查寝/提醒 |
| — | `plum` | `#8B6B8A` | 劳动 |
| — | `slate` | `#6B7C8A` | 中性辅助 |
| `LxError #D84040` | `rose` | `#B5645A` | 错误/退出 |

**八色分类色板 `LxCategoryColors` 同步替换** 为：`slate #7B9EC9`、`terra #C4704B`、`sage #6B8F71`、`plum #8B6B8A`、`amber #C49A4B`、`slate-alt #6B7C8A`、`rose #B5645A`、`sage-alt #7A9A6E`（与原型课表 8 种课程色保持一致）。

**`MaterialTheme.colorScheme` 映射**：`primary → terra`、`secondary → sage`、`tertiary → amber`；深色主题暂不改动（按需再调）。

### 1.2 字体（`core/designsystem/theme/Type.kt`）

原型用 `Newsreader`（serif，标题/品牌字）+ `Outfit`（sans，正文/数字）。Android 端需：

1. 下载 Google Fonts 对应字重（Newsreader 400/500、Outfit 400/500/600），放入 `res/font/`
2. 定义 `FontFamily.Newsreader` 与 `FontFamily.Outfit`
3. `Typography`：`displayLarge / headlineLarge / headlineMedium / titleLarge` → Newsreader；其余 → Outfit
4. 数字场景（ring 百分比、进度值）必须 `fontFeatureSettings = "tnum"` 启用等宽数字

### 1.3 形状与间距

- 圆角层级：`r-sm = 8dp`、`r-md = 12dp`、`r-lg = 16dp`（当前 `LxCard` 写死 20dp 要改为 16dp）
- 卡片边框：`1.dp solid Color.Black.copy(alpha = .06f)`
- 卡片阴影：去掉（原型用细边框替代阴影，`cardElevation = 0.dp`）
- 页面水平 padding：`20dp`；卡片内 padding：`18dp 20dp`

---

## 2. 通用组件改造 ✅（Phase 9A / 9C）

> Phase 9C 决策：`LxCard` 的 1dp 暖色细边框已移除（视觉差≈0，6% 透明度肉眼不可见）。

### `LxCard`
- 圆角 16dp、无阴影、1dp 暖色细边框、`#FFFDF9` 底色
- 按压态：`onClick` 版本 press 时背景切到 `cream #FAF7F2`

### `LxButton`
- 主按钮：`terra` 填充 + 白字 + 16dp 圆角 + 48–52dp 高度
- 次按钮：`sand` 填充 + `ink-muted` 字

### `LxTextField`
- 背景 `cream`、边框 `sand-deep`、聚焦态边框切 `terra`、圆角 12dp、高度 48dp

### `LxTopBar` / 底部 Tab
- 底部 Tab：半透明 `parchment .92 + blur`、选中态 `terra`、未选中 `ink-faint`

---

## 3. 页面层改造清单

### 3.1 登录页（`feature/login/ui/LoginScreen.kt`）✅ Phase 9A

**当前现状：** 无插画；显示「轻小信」大字 + 「LightXin」副标题 + 两输入框 + 登录按钮。

**改造差距（大）：**

- [ ] **删除**：「轻小信」displayLarge 标题、「LightXin」副标题、「登录即同意用户协议…」脚注（若后续加上）。密码框「显示/隐藏」图标保留。
- [ ] **新增**：顶部带框插画，框 `margin 56.dp 28.dp 0`、`cream` 底 + 1dp 暖色细边框 + 16dp 圆角；与输入卡左右对齐
- [ ] **插画内容**（Compose Canvas 或静态 SVG vector drawable 皆可）：
  - 左卡：课程列表（3 行彩色竖条 + 占位条）
  - 右上：圆时钟，10:10 经典姿态，半径 32dp，红色秒针 60s 旋转
  - 右中：通知药丸卡（圆角 14dp + 图标 tile + 三行占位条）
  - 底部：**橙色装饰曲线**，`stroke terra opacity .32`，穿行于卡片之后
  - 以上四元素各自独立的浮动动画（Y 轴 ±6dp + 轻微 rotate，周期 7–8s，错相）
- [ ] **表单卡**：水平 margin 28dp、justify-content flex-start、`padding-top 38.dp`（整体下移避免下部留白过多）
- [ ] 品牌字/脚注的位置**全部让给插画**——登录页不再承载品牌记忆，由欢迎页承担

### 3.2 欢迎页（**新建**）✅ Phase 9A

**当前现状：** 项目中**完全不存在** Onboarding/欢迎页，也无首启判断逻辑。

**新建内容：**

- [ ] `feature/onboarding/ui/OnboardingScreen.kt`
- [ ] 首启判断：`DataStore preferences key = "lxin_onboarded: Boolean"`；`false` 时路由到 Onboarding，点"我已知晓"后置 `true` 并跳登录；**退出登录不重置此标志**
- [ ] 上半屏插画（viewBox 390×280）：
  - 校园晨景：天空/地平线晕光/路径/太阳+光芒/云朵/三栋建筑（宿舍楼 + 主楼 + 右楼）/树木/灌木
  - **晨光呼吸**：`horizon glow ellipse opacity .06 ↔ .22`，14s 周期
  - **两个走路小人影**：沿中央小径从底部走向主楼门（translateY 0→-78px / -70px，18s / 22s 错开，两端各 opacity 淡入淡出）
  - 窗户/太阳/树有各自动效（参考原型 `winGlow / sunFloat / rayPulse / treeSway / cloudDrift`）
- [ ] 下半屏内容区：
  - **不要**「轻小信」四字大标题
  - **「更轻量，更舒适的校园体验」** 用 Newsreader serif 22sp `terra` 字色，承载品牌气质
  - 描述段 13.5sp / `ink-muted`
  - 两枚按钮：「我已知晓」`terra` 主按钮 + 「暂不进入」`sand` 次按钮（52dp 高）
- [ ] Stagger 淡入（参考 `StaggeredCard`，每项延迟 50ms）

### 3.3 首页（`feature/home/ui/HomeDashboard.kt`）✅ Phase 9A 初版 → **Phase 9C 叙事架构重构**

> ⚠️ 本节为 Phase 9A 初版规划。Phase 9C 重新定义了首页：从 "4 卡 dashboard" 改为 "此刻我该做什么"叙事结构（**删除劳动卡、新增"下一节" headline、查寝条件渲染、运动卡删总进度条、Badge 去胶囊**）。以下原初版细节与 §9 冲突处以 **§9 为准**。

**当前现状：** 4 卡结构已就位（今日课程/查寝/运动/劳动），问候语、StaggeredCard 动画齐全。

**改造差距（中）：**

- [ ] **卡片容器**：`DashboardCard` 内部用新的图标小方块（32×32 圆角 8dp + 语义底色 + 同色图标），替换当前 "icon + 文字" 并排布局
  - 今日课程：`terra-soft` + terra 图标
  - 查寝：`amber-soft` + amber
  - 运动：`sage-soft` + sage
  - 劳动：`plum-soft` + plum
- [ ] **标题**用 Newsreader serif 16sp
- [ ] **Badge**（第 N 周 / 共 X 时）：`terra-soft` 底 + `terra` 字 + 20dp 胶囊圆角
- [ ] **今日课程卡**：左侧 3×36dp **彩色竖条** 替换当前的 8dp 圆点（与原型 `.c-bar` 一致，高度与两行文字齐）
- [ ] **查寝卡**：`ck-pulse` 8dp amber 圆点 + `pulse` 呼吸动画（`InfiniteTransition` animateFloat alpha 1 ↔ 0.35，2.5s）
- [ ] **运动卡**（重点）：
  - 保留当前 ring 布局，但 ring 尺寸 54dp、stroke 4.5dp、`sage` 色、`strokeCap = Round`
  - **环内 `78%` 文字强制 flex 居中**：`Box(contentAlignment = Center) { Text(..., lineHeight = 1, fontFeatureSettings = "tnum") }`
  - ring 右侧 `run-info` 设置 `minHeight = 54.dp` + `Arrangement.Center` 保证与 ring 视觉中线严格对齐
  - 文案：`run-main = "今日 2.35 km"`（15sp medium）+ `run-sub = "目标 3 km · 离完成还差 0.65 km"`（12sp `ink-muted`，line-height 1.35）
  - 下方**新增总进度横条**：label `总进度`（注意：**不是"本周"**）+ 9dp 高、5dp 圆角、`sand` 底 + `sage` 填充 + 右侧 `42 / 60 km`
  - **不要**用 `border-top` 分隔线（原型已确认去除）
- [ ] **劳动卡**：改成 `5 行 label-value` 简列（志愿/暑期/劳动/社区/其他），底色横条移除。数值右对齐、`font-variant-numeric tabular-nums`
- [ ] **问候语** `headlineLarge` 改用 Newsreader 30sp、副标题 14sp / `ink-muted`；智能副标题逻辑 `buildSmartSubtitle` 保留原样

### 3.4 课表（`feature/schedule/ui/ScheduleScreen.kt`）✅ Phase 9B

**当前现状：** `FilterChip` 周选择器；课程色块是**半透明背景 + 彩色文字**（15% alpha）；今日列 `primaryContainer.copy(.15f)` 高亮。

**改造差距（中大）：**

- [ ] **周选择器**换成自绘 `Row` + 胶囊 chip：
  - `.wk`（默认）：透明底、`ink-muted` 字、20px padding + 20dp 圆角
  - `.wk.cur`（本周）：1dp `ink-muted` 边框 + `ink-soft` 字 + `FontWeight.Medium`
  - `.wk.on`（选中）：`ink #2D2A26` 填充 + `cream` 字
  - `.wk.on.cur`（既本周又选中）：只显示填充，边框 transparent
  - `FilterChip` 组件无法简洁承载三态，建议自写
- [ ] **课程色块**改为 **实色填充 + 白字**，不再是半透明背景
  - 文字 `#FFFFFF` 10sp medium + 教室 9sp `rgba(255,255,255,.7)`
- [ ] **跨节课多格渲染 z-index 问题**：当前代码用 `Modifier.padding(top = top.dp)` 在同一 Box 内绘制，没有跨节叠层问题；但如果改成 grid 布局（每节一个 cell）需要注意：跨 2 节课的色块会溢出到下一 cell，必须让它 `zIndex(2f)` 以盖过后续 cell 的半透明底
- [ ] **今日列高亮**：底色从 `primaryContainer.copy(.15f)` 改为 `terra-glow rgba(196,112,75,.06)`
- [ ] **表头今日标签**：字色 `terra` + bold + 下方 4dp 圆点指示符
- [ ] 课程详情 BottomSheet 背景 `card`、圆角 `r-lg`

### 3.5 我的页（`feature/home/ui/ProfileScreen.kt`）✅ Phase 9B → **Phase 9C 精简**

> Phase 9C 追加：删除 `功能`/`其他` 两个 section label（改用 22dp spacer 自然分组）；菜单图标改 22×22 `Icons.Outlined.*` + `LxInkSoft`，取消四色底块；退出登录改为**陶土轮廓按钮**（与登录的实心陶土做视觉区分）。详见 §9。

**当前现状：** 每个 MenuRow 被包成独立 `LxCard`，视觉过重。

**改造差距（小）：**

- [ ] 功能列表**合并为一张 LxCard**，内部用 3 个 `grow` 行 + 中间分隔线（left 50dp + right 18dp 的 1dp `sand` 线）；按压态 `cream` 背景
- [ ] 每行：32×32 圆角 8dp 图标方块（语义色底） + 15sp 文字 + 右箭头（`›` 15sp `ink-ghost`）
- [ ] 头像卡：`sand` 底圆 + Newsreader 24sp terra 字（取用户名首字），**不再用** `Icons.Default.Person`
- [ ] 退出登录：改用 `LxCard` 样式（`rose` 字色 + 居中），不用 `LxOutlinedButton`
- [ ] 标题 "我的" 用 Newsreader serif 28sp

---

## 4. 图标替换说明 ⚠️

**约定：此次视觉重构期间，不改动任何图标相关代码。**

- 当前所有图标使用 `androidx.compose.material.icons.*`（`Icons.Default.Bed / CalendarMonth / DirectionsRun / WorkHistory / School / Person / Info / ChevronRight` 等）
- 原型 HTML 中的 SVG 图标样式是**示意用**，非最终资产
- **由用户本人**在实现 UI 时统一替换为应用最终图标集（可能来自 iconfont、Lucide、自定义 vector drawable 或其他来源）
- 代码侧只负责预留图标位置（`ImageVector` 参数或 `Painter` 占位），容器外观（底色方块、圆角、尺寸）按本文档实现即可
- 替换图标时**不要改变容器尺寸**（32×32 dp、圆角 8dp、`*-soft` 语义底色），保持统一视觉重量

---

## 5. 动效映射表（CSS keyframes → Compose）

| 原型 CSS | Compose 实现 |
|---|---|
| `@keyframes rise`（stagger 淡入上移） | 已有 `StaggeredCard` ✓ |
| `@keyframes ringFillAnim`（环形填充） | `animateFloatAsState(0f → progress, tween(2200, easing = CubicBezier))` 驱动 `CircularProgressIndicator.progress` |
| `@keyframes pulse`（呼吸） | `rememberInfiniteTransition.animateFloat(1f ↔ .35f, 2500ms, Reverse)` |
| `@keyframes cardFloat1/2/3`（登录插画卡浮动） | `animateFloat Y = -6..6dp, rotate`，周期 7–8s，三个卡 delay 错相 |
| `@keyframes secondSpin`（时钟秒针） | `rotate 0→360°`，60s 线性循环 |
| `@keyframes cloudDrift / treeSway / winGlow / sunFloat / rayPulse` | Canvas 绘制 + `InfiniteTransition`，用于欢迎页插画 |
| `@keyframes walkUp / walkUp2`（小人走路） | `translateY 0 → -78.dp / -70.dp`，18s / 22s 线性；两端 fade in/out |
| `@keyframes dawnShift`（晨光呼吸） | ellipse alpha `.06 ↔ .22`，14s ease-in-out |

---

## 6. 工作量估算

| 模块 | 工作量 | 优先级 | 状态 |
|---|---|---|---|
| 色板替换（Color.kt + Theme.kt） | S | P0 | ✅ 9A |
| 字体接入（Newsreader + Outfit + Noto Serif SC） | M | P0 | ✅ 9A/9C |
| 通用组件（LxCard/Button/TextField）重做 | M | P0 | ✅ 9A/9C |
| 登录页插画 + 框 + 表单重排 | L | P1 | ✅ 9A |
| 欢迎页（从零新建 + Onboarding 路由） | L | P1 | ✅ 9A |
| 首页 4 卡片视觉细节 | M | P1 | ✅ 9A → 9C 重构叙事架构 |
| 课表三态周选择器 + 实色块 + 今日指示 | M | P2 | ✅ 9B |
| 我的页合并列表卡 | S | P2 | ✅ 9B → 9C 精简 |
| 图标统一替换 |  | 最后 | ⏳ 由用户自行替换 |

S ≤ 0.5d，M ≤ 1d，L ≤ 2d。总估算 1 周内可完成 P0+P1。

---

## 7. 设计评审沉淀（关键决策）

以下是设计讨论中明确过、不再回滚的决策，实现时需遵守：

1. **运动卡片**：保留 ring + 下方总进度横条的**双进度**结构；去掉 `border-top` 分隔线；进度条 9dp 高、`sand` 底 + `sage` 填充；label 用 **"总进度"**（历史曾误写成"本周"，已确认改）
2. **环内 `78%` 文字居中**：`line-height: 1` + `fontFeatureSettings = "tnum"` + `padding-left 1px` 光学补偿
3. **ring + 右侧两行文字整体居中**：`run-info` 显式 `minHeight = 54.dp` + 内部 flex center，解决行高导致的视觉偏上
4. **课表周选择器**：`.cur` 指"本周"、`.on` 指"选中"；两态独立，可同时存在；切到其他周时 `.cur` 仍留在当前真实周次提示位置
5. **登录页不承载品牌字**：删除「轻小信」大字和「登录即…」脚注，品牌气质交给欢迎页的副标题
6. **登录页插画必须被框住**：与表单左右对齐（`margin horizontal 28dp`）保证视觉轴线统一
7. **欢迎页插画不框住**：hero 全出血，与登录页做差异化（欢迎是入口、登录是工具）
8. **欢迎页副标题承载品牌**：「更轻量，更舒适的校园体验」用 Newsreader serif 22sp `terra`，接替「轻小信」大字的角色
9. **登录页插画已放弃的元素**：课表网格卡（与左侧课程列表同质）、桌面铅笔、咖啡杯 —— 不要再加回
10. **登录页插画最终四元素**：课程列表卡（左）、圆时钟（右上，10:10 姿态，半径 32dp）、通知药丸（右中）、橙色有机曲线（贯穿底层）
11. **圆时钟不可过大**：半径 32dp 是最终尺寸，不是 50dp（早期版本偏大已放弃）
12. **图标由用户自行替换**：代码改造期间不动任何图标资源与引用

---

## 8. 参考

- 定稿原型：`prototype/anthropic-redesign.html`
- 前端色板/字体参考：文件头部 `:root` CSS 变量（所有 token 值与本指南第 1 节一一对应）
- 动画参考：文件中 `@keyframes` 段

---

## 9. Phase 9C — 首页 / 我的页 精炼补遗 ✅ 已落地

> 本节（原独立文件 `Phase9C-首页我的页精炼补遗.md`，已并入本指南）只记录 Phase 9C 评审新增的决策与容易踩坑的点，不复述 §1–§7 已定稿内容。
>
> 适用范围：首页（HomeDashboard.kt）与我的页（ProfileScreen.kt）的 Phase 9C 精炼，以及全局字体/Tab bar 细节。

### 9.1 字体（最高优先级踩坑点）✅

- **中文衬线必须加 fallback**。`Newsreader` 只覆盖拉丁字形，中文字符会 fallback 到系统黑体——这是首页 "字体没改" 的根因。
- 方案：在 `NewsreaderDisplay / NewsreaderLarge / NewsreaderSmall` 的 `FontFamily` 链里追加 **`Noto Serif SC Regular (400)`**（`res/font/notoserifsc_regular.ttf`）。
- **字重只下 Regular，不要下 Medium**。中文笔画密度远高于拉丁，Regular 中文配 Medium Newsreader 才视觉对齐；两边都 Medium 会让中文 "黑一块"。实现上把同一个 Regular TTF 注册为 `FontWeight.Normal` + `FontWeight.Medium` 两条 `Font` 条目以兜底。
- 需要强调时**加大字号**，不要加字重。
- Claude.ai 其实只用系统黑体（PingFang / YaHei）——我们走得比它更远，这是有意为之。

### 9.2 首页叙事架构（核心改变）✅

**首页不是 dashboard，是回答 "此刻我该做什么"。** 结构：

```
问候（陶土衬线）
副标（衬线 italic 墨灰）
—— 陶土横线 ——
"下一节" headline（叙事主角 · 非卡片）
今日课程卡
运动进度卡
(查寝签到卡 — 仅时段内显示)
```

- **删除劳动教育卡**——低频数据，移至 "我的" 即可（已在其中）。
- **查寝签到做成条件渲染**：只在查寝窗口临近（开始前 4 小时内）才出现。
- **"下一节" headline 与今日课程卡会有信息重叠**（比如下一节也出现在课程列表中），**这是有意的冗余**——headline 回答 "此刻"，卡片给 "全貌"。

### 9.3 陶土色使用准则 ✅

- **一屏最多 2 次**：首页 = 问候主标 + 横线；我的页 = "我的" 标题 + 头像陶土字。再多就稀释了。
- **实心陶土 = 主 CTA**（比如登录按钮）；**轮廓陶土 = 次级/确认/destructive**（比如退出登录）。不要混用。
- Badge / Icon / 小 badge 都**不用**陶土色——交给墨灰或真分类色。

### 9.4 图标色块 = 最后一个 Material 残留 ✅

- **首页卡头全部去掉 30×30 彩色圆角 icon chip**，卡头只剩 `[serif 标题] ────── [墨灰副数据 badge]`。
- **我的页菜单图标同上**：22×22 墨灰线性图标（`tint = LxInkSoft`），取消 amber/plum/sage/slate 四种底色。
- **唯一保留的彩色**：今日课程条目左侧 3dp 色条——这个和课表页的块色联动，保留能维持 "分类" 语义。

### 9.5 Badge 的正确用法 ✅

- **去掉胶囊背景**，纯 12sp 墨灰 sans 小字。
- **只承载卡片内没有的信息**。示例：
  - 今日课程 badge: `第 N 周 · 星期六`（课数在列表里可见，所以放星期）
  - 运动进度 badge: `目标 3 km`（body 写 "离完成还差 X km"，互补不重复）
- 反例：`今日 2.35 km` + body `今日 2.35 km` —— 重复。

### 9.6 分段横线（Section Rule）✅

- `1dp` 高、`LxTerra.copy(alpha = 0.18f)`、水平 padding 与文本齐（24dp），不要内缩 40dp 否则突兀。
- **只在叙事切换时用**：问候 → 下一节。不要往每张卡之间都加。

### 9.7 主 / 副标题字族必须统一 ✅

- **副标题也要衬线**：`greeting-sub` 从 `Outfit sans` 改成 `NewsreaderLarge` + `FontStyle.Italic` + `color = LxInkMuted`。
- 否则主标陶土衬线 + 副标灰色黑体会有明显 "字体栈割裂" 感。

### 9.8 我的页关键差异 ✅

- **删除 `功能` / `其他` 两个 section label**（Material uppercase 间距 1.8sp 的样式）。两个列表卡之间用 22dp spacer 自然分组。
- **退出登录 = 陶土轮廓按钮**（非实心）。实心陶土是登录按钮的视觉语言，退出不能和登录长得一样。1dp `LxTerra .55` 边框 + 16dp 圆角 + 52dp 高 + 陶土字，按压态切浅陶土填充 `alpha .06`。
- **头像保留沙色圆底 + 陶土字**（原型中尝试过 "衬线大字落款" 方案，用户否决，保持克制）。

### 9.9 卡片精简原则 ✅

- **等权重卡片堆叠 = dashboard**，按使用频率筛选：保留高频，删低频（或移位）。
- **每张卡只承载一个核心信息**。示例：运动卡原本有 "今日环 + 学期总进度 bar" 两重量级元素 → **只留今日**，学期放详情页。

### 9.10 其他细节 ✅

- **状态栏与页标题之间留 24dp**（原来 2dp 过紧）。首页、我的页都适用。
- **卡片 border**（`1dp solid rgba(45,42,38,.06)`）已在原型中移除。实际视觉差 ≈ 0（本来就 6% 透明度肉眼不可见），搬到 Kotlin 时 `LxCard` 也同步去 border 了。
- **Tab bar** 所有图标统一用**线性**版（`Icons.Outlined.*`），选中态靠**陶土色 + `FontWeight.SemiBold`** 区分，而不是切换成 `Icons.Filled.*`。避免顶部 Newsreader 衬线 vs. 底部 Material filled 的冲突。

### 9.11 查寝条件渲染具体实现 ✅

- 解析 `CheckinTask.startTime`（字符串 `HH:mm`），与当前 `LocalTime.now()` 比较分钟差。
- 显示窗口：`[start - 4h, start + 4h]`（简化版，覆盖 "临近" + "进行中" 两种场景；已签到的任务在 repo 侧已过滤）。

---

## 10. 落地后真机验证建议

- Phase 9C 后必须**上真机验证一遍**再决定是否继续打磨。Compose 真机字体渲染、动画手感与浏览器有差异（尤其是 Noto Serif SC 的 fallback 行为）。
- 所有 "已删除" 的 Compose 元素（卡片图标色块、sec-label、运动卡学期 bar、劳动教育卡）**直接从代码移除**，不保留占位。

