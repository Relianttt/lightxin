---
doc_type: issue-fix
issue: credit-exam-ui-polish
status: fixed
severity: minor
tags: [ui, bottom-sheet, color, exam, credit, labor]
---

# Fix Note：素质学分 BottomSheet 动画异常 + 考试成绩下拉框颜色反差

## 修复的问题

**Bug 1**：素质学分点击卡片后 BottomSheet 先全屏展开再回落到正常高度，产生异常动画。

**Bug 2**：考试成绩页下拉框（学年/学期）展开时背景为 Material3 默认冷紫灰（`#ECE6F0`），与全局羊皮纸暖色系形成强烈反差。

**Bug 3**：素质学分 / 劳动教育 BottomSheet 先展开再加载详情，导致 Sheet 高度从 loading 状态回落到内容高度，产生抖动。

## 根因

**Bug 1**：`CreditScreen.kt` 中 `rememberModalBottomSheetState(skipPartiallyExpanded = true)` 强制跳过半展开状态直接全屏，内容高度不足时系统再将其回落，产生两段动画。课表的 `ScheduleScreen.kt` 未传该参数（默认 `false`），行为正常。

**Bug 2**：`ExamScreen.kt` 中两个 `OutlinedTextField` 未传 `colors` 参数，`ExposedDropdownMenu` 未传 `containerColor`，完全走 Material3 默认色，与项目设计系统脱节。

**Bug 3**：`CreditViewModel` / `LaborViewModel` 的 `onRecordClick` 先置 `showDetailSheet = true` 再发请求，Sheet 展开时内容为 loading 状态（`LxLoading` 用 `fillMaxSize` 撑高），数据回来后高度缩小触发回落。课表无此问题因为点击时数据已在内存中。

## 修改

| 文件 | 改动 |
|---|---|
| `feature/credit/ui/CreditScreen.kt` | 去掉 `skipPartiallyExpanded = true`；加 `containerColor` / `shape` 与课表对齐 |
| `feature/credit/ui/CreditViewModel.kt` | `onRecordClick` 改为先请求详情、数据就绪后再置 `showDetailSheet = true` |
| `feature/exam/ui/ExamScreen.kt` | 两个 `OutlinedTextField` 加 `colors`；两个 `ExposedDropdownMenu` 加 `containerColor = LxCream` |
| `feature/labor/ui/LaborViewModel.kt` | 同 CreditViewModel，`onRecordClick` 改为先请求再展开 |

## 验证

- 素质学分 / 劳动教育点击卡片，BottomSheet 展开时内容已就绪，无高度回落抖动
- 素质学分 BottomSheet 无全屏回落动画
- 考试成绩下拉框展开后背景为 `LxCream`，边框/标签颜色与全局一致
