---
doc_type: feature-review
feature: 2026-05-30-grade-aware-sports-checkin
target: grade-aware-sports-checkin-design.md
status: changes-requested
date: 2026-05-30
reviewer: codex
---

# grade-aware-sports-checkin design review

## 结论

设计方向通过：年级分流、目标值只读服务端、`grade` 取自 `checkDsStudent.result`、`auto/clubInfo` 与 `index/clubInfo` 同名异路隔离、`checkState` 同名异义隔离、独立 ViewModel、QR 生成分层，这些判断都合理。

当前暂不建议改 `approved`，只剩一个文档结构问题需要先修：设计文档的 2.3 / 2.4 / 2.5 重复出现，应删除重复段并统一挂载点措辞。

## 澄清记录

- 原审阅中提出的“`getDashboard()` 升级会误改 App 主页运动卡”已撤回。
- 用户澄清：“主页不动”指 App 主页运动卡的 UI 形态与入口行为不变；数据应当复用升级后的个人真实运动进度。大二现在显示 0 是问题，修正为 `extraInfo.planMile/completeMile` 是预期行为。
- 因此 `getDashboard()` 可以作为统一运动摘要入口升级，供 App 主页与智慧运动页共同使用；但俱乐部信息卡、俱乐部详情、锻炼考勤入口仍只出现在智慧运动内部。

建议同步修改 design 中相关表述：

- “不改 App 主页运动进度卡”改为“不改 App 主页运动卡 UI 形态和入口行为；其数据源可随 `getDashboard()` 修正大一大二真实进度”。
- 验收第 11 条改为“App 主页运动卡 UI/导航不变；大一大二账号的数据从 0 修正为服务端真实进度”。

## 需修改项

1. 删除重复的 2.3 / 2.4 / 2.5 段落。
2. 统一挂载点“路由表”标注：这是修改既有 `Routes.kt` / `NavGraph.kt`，其中新增两个路由项。
3. 调整“主页不动”的措辞，避免后续实现者误以为 App 主页数据不能跟随修正。

## 已确认判断

1. `RunningDashboard` 不新增布尔字段可以，但不建议在 UI 层散落 `studentTypeLabel.contains("大一大二")`。建议把字符串判断收敛在 repository/mapper 内部；UI 显示俱乐部卡直接看 `clubSummary != null`。
2. 大二接口失败降级策略认同，但建议按接口独立降级：`extraInfo` 失败不抹掉成功拿到的 `clubSummary`，`clubInfo` 失败也不影响已拿到的课外跑步进度。
3. `requirement` 留空可以。running 模块当前无对应 requirement 文档，按规范由 acceptance 阶段触发 `cs-req backfill`。

## 通过条件

完成“需修改项”后，可将 design 状态改为 `approved` 并生成 `grade-aware-sports-checkin-checklist.yaml`。
