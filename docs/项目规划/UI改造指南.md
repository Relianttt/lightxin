# UI 改造指南（Anthropic 暖色系重设计）

> 目标：把当前 Material 蓝橙风改造成 `prototype/anthropic-redesign.html` 所定义的暖色、羊皮纸质感、serif + sans 双字族的视觉语言。
>
> 原型文件（定稿）：`prototype/anthropic-redesign.html`
>
> 本指南同时沉淀设计评审过程中明确的细节决策，避免二次返工。

---

## 0. 执行顺序建议

1. **设计 Token 层**（色板 + 字体 + 圆角 + 间距）—— 影响面最大，必须先做
2. **通用组件层**（`LxCard` / `LxButton` / `LxTextField` / `LxTopBar`）—— 承载 Token，变更后页面自动继承
3. **页面层** 按 登录 → 欢迎（新增）→ 首页 → 课表 → 我的 顺序改造
4. **图标替换** ⚠️ **由用户本人在实现 UI 时统一替换**，本次改造不动任何 `res/drawable` 或 Material Icons 引用；占位照旧，只改外观容器（底色块、圆角）

---

## 1. 设计 Token（必须先做）

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

## 2. 通用组件改造

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

### 3.1 登录页（`feature/login/ui/LoginScreen.kt`）

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

### 3.2 欢迎页（**新建**）

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

### 3.3 首页（`feature/home/ui/HomeDashboard.kt`）

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

### 3.4 课表（`feature/schedule/ui/ScheduleScreen.kt`）

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

### 3.5 我的页（`feature/home/ui/ProfileScreen.kt`）

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

| 模块 | 工作量 | 优先级 |
|---|---|---|
| 色板替换（Color.kt + Theme.kt） | S | P0 |
| 字体接入（Newsreader + Outfit） | M | P0 |
| 通用组件（LxCard/Button/TextField）重做 | M | P0 |
| 登录页插画 + 框 + 表单重排 | L | P1 |
| 欢迎页（从零新建 + Onboarding 路由） | L | P1 |
| 首页 4 卡片视觉细节 | M | P1 |
| 课表三态周选择器 + 实色块 + 今日指示 | M | P2 |
| 我的页合并列表卡 | S | P2 |
| 图标统一替换 |  | 最后 |

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
