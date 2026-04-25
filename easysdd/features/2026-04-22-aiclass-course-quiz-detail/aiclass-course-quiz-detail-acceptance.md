---
doc_type: acceptance
feature: aiclass-course-quiz-detail
slug: aiclass-course-quiz-detail
status: draft
date: 2026-04-22
summary: AI课堂课程详情、当前学期课程补数与测验列表功能验收记录，覆盖课程卡片跳转、FIF memberId 解析、漏课补齐、测验列表读取与架构归并
tags: [aiclass, course-detail, quiz, fif, navigation]
---

# AI课堂课程详情与测验列表 — 验收报告

## 1. 改动范围

本次功能改动覆盖：

- `core/network/FifSessionManager.kt`
  - 新增 `memberId` 持久化与 `getMemberUserId()`
  - 从 FIF token 的 JWT payload 解析 `memberId`
- `feature/aiclass/data/*`
  - 新增 `getPublishPaperListOfStudent`
  - 新增 `getPaperInCourseInfo`
  - 新增 `getTimetableInfo`
  - 新增 `AiClassQuizListResponse`
  - 新增 `AiClassTimetableResponse`
  - Repository 增加 `getQuizList(courseId)`，以学生列表为主合并课程详情试卷信息，并把课表缺失课程补进当前学期“我的课程”
- `feature/aiclass/domain/AiClassModels.kt`
  - 新增 `AiQuiz`
  - `AiCourse.displayName()` 支持补 `（理论）` / `（实验）`
- `feature/aiclass/ui/*`
  - 首页课程卡片改为可点击
  - 新增 `AiClassCourseDetailScreen`
  - `AiClassViewModel` 增加详情页状态与测验列表加载动作
- `navigation/*`
  - 新增 `AICLASS_DETAIL/{classId}` route
- `easysdd/architecture/aiclass-overview.md`
  - 归并课程详情与测验列表能力
- `app/src/test/java/com/lightxin/core/network/FifSessionManagerTokenParsingTest.kt`
  - 新增 token 解析单测

## 2. 检查项状态

| 检查项 | 状态 | 说明 |
|---|---|---|
| check-1: FIF token 中的 memberId 能被容错解析并持久化 | pending | 已补代码与单测，尚未跑 `gradlew test` |
| check-2: 测验提交状态优先使用 iscommited | passed | Repository 显式以 `iscommited` 映射 `AiQuiz.isCommitted` |
| check-2b: myClassroom 缺失的当前学期课程可由 timetableInfo 补进“我的课程” | pending | 已补课表接口与合并逻辑，仍需基于真实账号手工验证缺失课程是否出现 |
| check-3: 首页课程卡片点击后可进入课程详情页 | pending | 路由与 UI 已接通，需最后一轮重编译/手工验证 |
| check-4: 课程详情页能展示测验列表、空态与错误态 | pending | UI 与状态已接通，且历史测验读取策略已改为主列表合并补充字段；仍需最后一轮重编译/手工验证 |
| check-5: acceptance 阶段更新 aiclass-overview.md | passed | 已更新 |

## 3. 当前验证记录

### 3.1 已完成

- 用户本地执行 `:app:assembleDebug` 成功，说明在那一版代码上主功能链未引入构建阻断
- 之后我又顺手清理了两类告警：
  - `hiltViewModel` 新包迁移
  - `Icons.Filled.Login` 弃用替换
- 本轮又补了基于 `timetableInfo` 的当前学期课程补数逻辑：
  - 仅在 `schoolId + memberId` 可用时启用
  - 课表补数失败时回退为 `myClassroom` 原结果，不阻断首页加载
  - 对补进课程，详情/测验链路使用 `teachClassId(classId)` 作为 `courseId`

### 3.2 尚未补完

- 我所在环境运行 `gradlew test` 需要访问用户目录下的 Gradle 缓存，之前因为沙箱限制未完成
- 在用户成功编译后，我又把测验列表策略调整为“学生列表主数据 + 课程详情试卷补充合并”，并补了详情页列表 key 的兜底，因此需要基于最新代码再跑一次 `assembleDebug` 或 `test`

## 4. 结论

功能实现已完成，文档与架构归并已完成。

当前剩余的是最后一轮验证闭环：

1. 重新执行一次 `assembleDebug`
2. 最好再执行一次 `test`
3. 用存在“课表里有、我的课程里没有”的真实账号手工确认补数效果

这两步通过后，本 acceptance 可以把剩余 `pending` 检查项更新为 `passed`。
