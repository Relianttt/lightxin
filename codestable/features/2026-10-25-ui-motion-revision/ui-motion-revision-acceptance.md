---
doc_type: feature-acceptance
feature: 2026-10-25-ui-motion-revision
slug: ui-motion-revision
summary: UI 统一、模块改版与动效 v3 的验收记录：P1–P7 主体已完成，T2 回弹诊断与 T12-C 容器展开未做
status: active
date: 2026-10-25
tags: [ui, motion, labor, credit, checkin, running, cache, acceptance]
---

# UI 统一、模块修订与动效 v3 验收记录

> 设计输入：`ui-motion-revision-design.md`（原始实现报告）
> 验收日期：2026-10-25
> 结论：P1–P7 主体完成并通过构建与单元测试；T2 回弹诊断、T12-C 容器展开、release 录屏与部分真机核验未完成。

## 1. 逐项状态

| 编号 | 内容 | 状态 | 备注 |
|---|---|---|---|
| T1 | 劳动教育分页 | 完成 | `pageSize=100`、`total` 判定、`type:id` 去重、倒序、加载失败可重试；`LaborPagingTest` |
| T2 | 滑到底回弹延迟 | **未做** | 需 release 包真机四组对照 + 录屏；若指向 Pager 须先汇报 |
| T3 | 报修接入登录失效 | 完成 | 实测拒绝形态判定 + `RepairAuthRejectionTest`，重试为主 |
| T4 | 全局底色与加载态 | 完成 | 旧 token 别名指向新 token、ColorScheme 全量切换、`LxCard` 0.5dp 细边、骨架与统一页脚 |
| T5 | 登录失效 UI | 完成 | 首页/模块内统一为 `LxSessionBanner` 与精简 `ServiceErrorCard`；劳动教育有缓存时只挂横幅 |
| T6 | 劳动/学分/成绩改版 | 完成 | 三页页头 + 总览 + 无卡片记录行；公共月份组头/日期块/分段条/图例；详情弹层点击即开 |
| T7 | 劳动/学分持久化 | 完成 | 通用快照表 v2 自动迁移、`SnapshotStore`、缓存先显 + 后台刷新、失败保留数据与更新时间 |
| T8 | 模板页按钮 | 完成 | `LxActionRow` 分组，删除降为居中文案 |
| T9 | 签到结果底部弹层 | 完成 | 成功/失败弹层、同图重试、成功/失败触感，Snackbar 删除 |
| T10 | 查寝列表分区 | 完成 | “现在可以签到 / 全部记录”，不再写“已错过” |
| T11 | 跑步轨迹显示 | 完成 | `projectTrack` 按中心纬度 cos 修正、实时/结果/模拟三处展示、`TrackProjectionTest` |
| T12-A | 动效 token 与转场 | 完成 | `LxMotion` v3、页面/弹层/按压/分页吸附 |
| T12-B | 列表编排、计数、触感、预测性返回 | 完成 | 8 个列表 `animateItem` + 首屏错峰、计数与分段填充、四类触感、`enableOnBackInvokedCallback` |
| T12-C | 列表进详情容器展开 | **未做** | 需先给用户看录屏并确认 |

## 2. 验证记录

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest --rerun-tasks` | 191 项通过（含新增 `LaborPagingTest`、`ExamSummaryTest`、`SnapshotStoreTest`、`TrackProjectionTest`、`ExamTermsTest`） |
| `lintDebug` | 通过 |
| `assembleDebug` / `assembleDebugAndroidTest` | 通过（instrumentation 仅编译，未在用户设备执行） |
| `assembleRelease` | 通过，`adb install -r` 覆盖安装后冷启动存活 |
| `check_notes.py .agents/notes --agents AGENTS.md` | 0 错误 0 警告 |

未执行：`connectedDebugAndroidTest`（会部署/清理设备上的同包名应用，需用户确认）、release 动效录屏、T2 对照实验、T12-C 录屏确认。

## 3. 与规格的偏差

- 素质学分接口没有可靠的登录失效信号（HTTP 401 只映射成文案），有缓存时只显示“更新失败 · 点此重试”，不挂登录失效横幅。
- 分段条 `fill` 使用 `rememberLxCountUp(1f, delayMs = ContentDelayMs)`，比数值晚 `ContentDelayMs` 启动，与报告参数一致；数值本身仍为 `CountUpMs`。
- 考试学期下拉后续改为**按时间倒序**（`examTermOptions`），不再按学年顺序交替；成绩汇总卡片的绩点说明已删除。
- 课表页删除了“第 x 周（根据上次同步估算）”提示，同步时间只在刷新失败时显示；估算仍用于内部周次计算。

## 4. 落地后发现并修复的问题

- release 包登录后闪退：`ScheduleCacheMapper` 等使用匿名 Gson `TypeToken`，R8 丢掉泛型签名导致初始化异常。改用 `TypeToken.getParameterized` 并补 proguard keep；沉淀于 `2026-10-25-learning-gson-typetoken-r8.md`。
- 签到页分割线偏重：`LxDetailRow` 的 `outlineVariant.copy(alpha = 0.5f)` 是替换透明度而非乘算，实际约 44% 黑；改为 0.5dp + `LxHairline`，与卡片描边一致。
- 运动首页“任务进度 / 进度占比”去掉内层卡片与图标；跑步中页轨迹卡移到距离卡上方。

## 5. 剩余事项

1. **T2**：release 包，录屏请假/报修/查寝/路线模板四组，再做三个实验并回滚；结论指向 Pager 时先汇报。
2. **T12-C**：先提供容器展开的录屏，用户确认后再实现。
3. 真机核验：断网缓存与“更新于”提示、签到成功/失败弹层、查寝分区、轨迹形状、首屏错峰只播一次、四类触感、Android 14+ 预测性返回。
4. 深色模式截图核对与 `RecoveryUiInstrumentedTest` 真机执行（需可清空的测试设备）。

## 6. 相关提交

`ac6bd15`（P1–P3、T4/T8/T12-A）、`3e96d04`（P4 劳动/学分改版与快照）、`b4f3b45`（P5 签到弹层与查寝分区）、`c9679ba`（P6 轨迹）、`9d2b528`（P7 编排/触感/预测性返回）、`505737e`（分段条延迟）、`319100c`（release TypeToken 修复）、`0da60b3`（课表提示）、`8f538a6`（考试成绩学期排序）、`7e1df5f`（分割线与跑步页顺序）。
