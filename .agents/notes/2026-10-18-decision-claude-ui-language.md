---
doc_type: decision
category: convention
status: active
date: 2026-10-18
tags: [designsystem, ui, claude, tokens, components, leave, repair, running]
---

## 背景

请假 / 报修 / 路线模板三组页面上线后，逻辑跑通但 UI 被判定“太像 Material”。三轮重做（v1 → v2 → v3）后确认：问题不在配色，而在组件形状与信息焦点。

- v1 把“细线”当风格：每行分割线、tab 下划线、键值表线、指标分栏线、时间轴导轨，线成了装饰。
- v2 删掉装饰线、改用背景色阶与留白，但列表与详情失去焦点。
- v3 按 claude.ai 真实 token 重建，并补回内容锚点（日期块 / 故障类型缩略图 / 轨迹缩略图），同时按拍板去掉衬线标题、主按钮改陶土、日期时间改底部弹层、危险操作改实心 danger。

用户最终拍板：**无衬线标题、陶土主按钮、时间选择弹层、实心 danger 按钮**。

## 依据

- **claude.ai 应用 CSS**（`web.archive.org` 2026-02 快照，样式来自 `assets.claude.ai` 的 `6492ae367fc05efc.css`）：
  - Light：`bg-100 #FAF9F5`、`bg-200 #F5F4ED`、`bg-300 #F0EEE6`、`text-100 #141413`、`text-200 #3D3D3A`、`text-400 #73726C`、`accent-main-100 #C6613F`、`danger-100 #B53333`、`danger-900 #F9ECEC`。
  - Dark：`bg-100 #262624`、`bg-000 #30302E`、`bg-200 #1F1E1D`、`text-100 #FAF9F5`、`danger-100 #DD5353`。
  - 边框：`border-0.5 { border-width: .5px }`；圆角刻度 2/4/6/8/12/16/24/32。
- **docs.claude.com 的焦点做法**：侧栏选中项用 ink/10 底色，表格首列加粗、表头 ink/5 底，章节靠字号跳档；说明焦点来自底色、字重、字号，而不是加大留白。
- **claude.com 计算样式**：body `#FAF9F5`、主按钮 `#141413` 底 + 8px 圆角、卡片 24px 圆角 + 1px 极浅边 + 4% 软阴影。

完整 token 表和来源记录在 `prototype/claude-tokens.md`。

## 决定

1. **新增 Claude 并行 token 层**：`core/designsystem/theme/ClaudeTokens.kt`，提供 `LxCanvas / LxSurface / LxTint / LxTintStrong / LxText / LxTextSecondary / LxTextMuted / LxTextFaint / LxHairline / LxAccent / LxAccentText / LxOnAccent / LxStateSuccess / LxStateWarning / LxDanger / LxDangerFill / LxDangerSurface`。旧 `LxParchment / LxInk / LxTerra` 不动，避免未迁移页面被连带改色。
2. **组件并行新增**：`ClaudeComponents.kt`（`LxNavBar / LxPageHeader / LxSectionLabel / LxStatus / LxFilterTabs / LxPill / LxPanel / LxCallout / LxStepBar / LxFlowStep / LxMetaGrid / LxMetaRow / LxPrimaryButton / LxGhostButton / LxDangerButton / LxField / LxInput / LxSelectRow / LxGroupCard / LxGroupRow / LxEmptyState / LxErrorState / LxSkeleton*`）与 `ClaudeSheets.kt`（`LxBottomSheet / LxConfirmSheet / LxOptionSheet / LxDateTimeSheet`）。
3. **页面骨架**：`LxNavBar`（只有返回与右侧文字动作，无居中标题）+ `LxPageHeader`（26sp/600 sans，可选副标题）；旧 `LxTopBar` 保留给未迁移页面。
4. **信息层级**：文字三级 `text-100/200/400`；标题 600；值 600；状态用 5dp 圆点 + 12sp 中黑；不再用彩色底纹胶囊。
5. **列表焦点**：每行一个内容锚（日期块 / 类型图标或照片 / 轨迹缩略图），标题与状态形成第二层；列表不画分割线，靠 15dp 行距与 24dp 边距。
6. **详情焦点**：请假 = 状态块 + 横向三步进度 + 白色时间块 + 粗值网格；报修 = 状态块 + 96dp 现场图 + 粗值网格 + 流程点列；模板 = 轨迹 hero 卡（预览 + 三个指标）+ 元信息行 + 文字动作 + 设为默认 CTA。
7. **主按钮**：陶土 `#C6613F` 底 + 近白字，8dp 圆角、50dp 高；暗色 `#D97757` 底 + 深色字；文字链降到 `#B4562F` 保证可读。
8. **危险操作**：实心 `#B53333` 底 + 白字（`LxDangerButton` / `LxConfirmSheet(destructive=true)`）；红色只做按钮/文字，不做整页强调。`danger-900` 柔和底保留在 token 中作为备选。
9. **时间选择**：自绘 `LxDateTimeSheet`（月历 + 时分 stepper + 取消/确定），替换 Material `DatePickerDialog` 与 `TimePicker`。
10. **文案**：页面文案统一用全角引号 `“ ”`，不再用 `「 」`（`app/src` 已整体替换）。
11. **范围**：本次只迁移请假、报修、路线模板三组页面。登录、首页、课表、查寝等仍走旧 token；后续迁移时再切 `LightXinTheme` 的全局 ColorScheme，避免一次改动把未评审页面一起改色。

## 理由

- Claude 的克制不是“更少元素”，而是把结构交给底色和字重；线只保留 0.5dp 的功能性边框（输入框、白卡）。
- 列表标题取什么由功能层决定；视觉层只规定“标题降一级、锚点升一级”的层级关系，避免用加大留白代替信息组织。
- 陶土主按钮与实心 danger 是用户明确拍板；`#C6613F` 白字对比 4.05:1，用户接受，保持不变。
- 并行 token 层避免 15 个旧组件与 8+ 个未迁移页面被一次性改色；迁移成本集中在新页面。

## 考虑过的替代方案

- **直接改 `LightXinTheme` 的全局 ColorScheme**：一次改完整 app，但未评审页面（首页/课表/登录）会出现未验证的对比度与视觉变化，不采用。
- **继续用旧 `LxTopBar / LxCard / LxChoiceChip` 改样式**：会把旧页面一起改掉，回退粒度变大；改为新增并行组件。
- **Material DatePicker / TimePicker**：与无衬线 + 纸面风格冲突；改为自绘弹层。
- **危险操作保留红色文字行**：用户明确不喜欢；改实心 danger。

## 后果

- 代码里同时存在两套色板/组件；迁移页面必须显式使用 `LxCanvas` 等新 token，不能再用 `MaterialTheme.colorScheme.background`。
- 新页面在 dark mode 下走 `isSystemInDarkTheme()`，与旧页面 dark 行为独立。
- `TrackCanvas` 的颜色与缩略图尺寸改为新 token（46dp / `LxTint`），会影响跑步模板列表/详情的所有入口。
- 全角引号替换是纯文案改动，不影响协议字段。

## 验证

- `testDebugUnitTest`：122 通过，0 失败
- `lintDebug`：0 error / 103 warning / 4 hint
- `assembleDebug` + `installDebug`：成功
- Pixel 4 / Android 13 真机走查：请假列表/详情/发起 + 日期弹层、报修列表/详情/新建、模板列表/详情/导入入口；浅色与深色各看一轮。
- 已知问题：拍照入口在本机仍无法拉起系统相机，已做 `ActivityNotFoundException` 兜底并记录在 `.agents/notes/2026-10-18-learning-camera-intent-not-found.md`。

## 后续修订

2026-10-18 真机走查后，以下几条被 `.agents/notes/2026-10-18-decision-mobile-first-ui-revision.md` 取代：

- 决定 3：导航栏不再“无标题”，滚动后显示小标题。
- 决定 4：值不再一律 600，改为常规字重；长值独占一行。
- 决定 6：模板详情不再用文字动作，改为普通按钮 + 底部独立删除。

另：迁移范围也扩到「学工在线」的查寝列表（见新 note 决定 14）。

其余（并行 token 层、页面骨架、主按钮、危险操作实心、时间弹层、全角引号）继续有效。

## 相关文档

- `.agents/notes/2026-10-18-decision-mobile-first-ui-revision.md`
- `codestable/architecture/designsystem-overview.md`
- `codestable/architecture/DESIGN.md`
- `prototype/claude-ui-refactor-v3.html`
- `prototype/claude-tokens.md`
- 代码：`core/designsystem/theme/ClaudeTokens.kt`、`component/ClaudeComponents.kt`、`component/ClaudeSheets.kt`
