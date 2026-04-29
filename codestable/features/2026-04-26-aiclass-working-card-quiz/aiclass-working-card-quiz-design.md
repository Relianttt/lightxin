---
doc_type: feature-design
feature: aiclass-working-card-quiz
slug: aiclass-working-card-quiz
status: approved
date: 2026-04-26
summary: "正在上课"卡片绑定点击事件跳转测验详情，同时为课程卡片和详情页补齐（理论）/（实验）后缀显示
tags: [aiclass, working-record, quiz, navigation, typename]
---

# "正在上课"卡片点击跳转测验详情 + 理论/实验后缀

## 0. 需求摘要

### 做什么

1. **"正在上课"卡片加点击**：点击后跳转课程详情页，展示测验列表，行为和"我的课程"普通卡片一致
2. **理论/实验后缀**：课程名后根据 API 返回的 `typeName` 字段补上 `（理论）` 或 `（实验）`

### 为谁做

学生视角 — 正在上课时能从首页直接进入测验列表，不用先去"我的课程"找对应卡片

### 成功标准

- 首页"正在上课"卡片可点击，点击后进入课程详情页，能看到该课程的测验列表
- 课程名正确显示（理论）/（实验）后缀
- 点击返回后回到首页，首页状态不受影响

### 明确不做

- 不修改"正在上课"卡片的视觉样式（仅加点击行为）
- 不新增 API 接口
- 不改变"我的课程"列表的显示逻辑（不补隐藏课程）
- 不做测验答题
- 不给数字码签到卡片加点击

## 1. 设计方案

### 1.1 数据流

```
用户点击"正在上课"卡片
    → AiClassViewModel.openWorkingRecordDetail()
        → 在 uiState.courses 中按 teachClassId 反查已有 AiCourse
        → 找不到则用 WorkingRecord 构建临时 AiCourse（stableId = "_working"）
        → 设置 selectedCourse，发起 getQuizList(teachClassId)
    → 导航到 AICLASS_DETAIL/_working
    → AiClassCourseDetailScreen 展示课程信息 + 测验列表
```

### 1.2 typeName 来源

| 场景 | typeName 来源 |
|------|-------------|
| "我的课程"普通卡片 | `CourseItem.typeName`（myClassroom API 已返回，模型已声明） |
| 课表补数课程 | timetable 的 `className` 字段解析（`parseTypeName` 已有） |
| "正在上课"卡片 | 优先从主列表匹配的 AiCourse 取 typeName；匹配不到则为空 |

### 1.3 displayName() 逻辑（已存在，本次不改）

```kotlin
// 来源：AiClassModels.kt displayName()
fun AiCourse.displayName(): String {
    if (courseName.isBlank()) return courseName
    return when {
        typeName.contains("实验") && !courseName.contains("实验") -> "${courseName}（实验）"
        typeName.contains("理论") && !courseName.contains("理论") -> "${courseName}（理论）"
        else -> courseName
    }
}
```

`!courseName.contains("实验")` 防止二次拼接——如果课程名本身已含"实验"，不再重复加后缀。

### 1.4 改动文件清单

| 文件 | 改动 |
|------|------|
| `AiClassViewModel.kt` | 新增 `openWorkingRecordDetail()`；`openCourseDetail()` 处理 `"_working"` 提前返回 |
| `AiClassHomeScreen.kt` | `WorkingClassCard` 加 `onClick` 参数；AiClassContent传入回调 |
| `NavGraph.kt` | AiClassHomeScreen 调用处接上 `onOpenWorkingDetail` 回调 |

不碰的文件：`AiClassCourseDetailScreen.kt`（已有逻辑兼容）、`AiClassModels.kt`（displayName 不改）、`AiClassRepository.kt`（不改）

## 2. 验收标准

### 2.1 点击"正在上课"卡片

- [ ] 首页有"正在上课"卡片时，点击能跳转到课程详情页
- [ ] 详情页显示课程名（含理论/实验后缀，如有）和测验列表
- [ ] 测验列表数据来自 `getQuizList(teachClassId)`
- [ ] 从详情页返回后，首页状态正常

### 2.2 "正在上课"卡片无匹配课程时

- [ ] 用 record.courseItemName 作为课程名展示，不崩溃
- [ ] 测验列表正常加载

### 2.3 理论/实验后缀

- [ ] "我的课程"列表中，typeName="理论"的课程名后显示（理论）
- [ ] "我的课程"列表中，typeName="实验"的课程名后显示（实验）
- [ ] 课程详情页标题同样显示后缀
- [ ] 课程名本身已含"实验"时不重复拼接

### 2.4 边界

- [ ] 首页无"正在上课"时，不显示该卡片（现有行为，不退化）
- [ ] "正在上课"卡片点击后 loading 态正常（isQuizLoading 覆盖）
- [ ] 快速双击卡片不会重复导航

## 3. 推进步骤

### 第 1 步：ViewModel 加 openWorkingRecordDetail()

**文件**：`AiClassViewModel.kt`（追加，约 30 行）

- 从 `_uiState.value.workingRecord` 取当前课堂记录
- 在 `courses` 中按 `teachClassId` 反查，找到则复用该 AiCourse
- 找不到则构建临时 AiCourse（stableId = "_working"，courseId/teachClassId 用 record 的值）
- 设置 `selectedCourse` + 清空 `quizList` + `isQuizLoading = true` + `quizError = null`
- 发起 `getQuizList(record.teachClassId)`，协程内加陈旧数据防护：`if (current.selectedCourse?.stableId != "_working") return@update current`
- 修改 `openCourseDetail()`：`classId == "_working"` 时直接 return

**退出信号**：编译通过，函数可被外部调用

### 第 2 步：HomeScreen 改造 WorkingClassCard

**文件**：`AiClassHomeScreen.kt`（追加，约 10 行）

- `WorkingClassCard` 加 `onClick: () -> Unit` 参数
- `LxCard(onClick = onClick)` 包裹现有内容
- `AiClassContent` 签名加 `onOpenWorkingDetail: () -> Unit`，传给 WorkingClassCard

**退出信号**：编译通过，WorkingClassCard 在 Preview 中可点击

### 第 3 步：NavGraph 接线

**文件**：`NavGraph.kt`（追加，约 6 行）

- `AiClassHomeScreen` 调用处加 `onOpenWorkingDetail` 回调
- 回调内：`viewModel.openWorkingRecordDetail()` + `navController.navigate(Routes.aiClassDetail("_working"))`

**退出信号**：编译通过，debug 包点击"正在上课"卡片能跳转详情页

### 第 4 步：实机闭环验证

- debug 包测试：点击"正在上课"卡片 → 看课程名后缀 → 看测验列表
- release 包测试：同上（验证 ProGuard 不影响）
- 验证"我的课程"列表里的理论/实验后缀是否正确

**退出信号**：debug 和 release 包均测试通过
