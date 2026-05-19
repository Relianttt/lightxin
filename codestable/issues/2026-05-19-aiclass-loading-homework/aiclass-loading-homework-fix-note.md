---
doc_type: issue-fix
issue: aiclass-loading-homework
status: fixed
severity: major
tags: [aiclass, homework, quiz, loading, ui]
---

# Fix Note：AI 课堂测验 / 作业加载与提交样式

## 问题

- AI 课堂首页和课程详情页在部分 FIF 接口响应慢时长期显示 loading。
- 作业列表接口请求中 `teachClassId` 可能为空，导致后端返回空列表。
- 作业详情入口 URL 的 `jtzy` 在 hash route query 中，旧解析逻辑拿不到 token。
- 测验列表响应实际为 `data[].paperDetail[]` 分组结构，旧模型按扁平列表解析。
- 提交作业 BottomSheet 使用 Material 默认按钮和输入框颜色，和现有暖色设计系统不一致。
- 部分 AI 课堂课程的 `typeName` 为空，理论 / 实验标识藏在教学班显示字段里，导致轻小信课程卡片没有显示原 App 中的理论 / 实验后缀。

## 修复

- `AiClassViewModel` 给首页、测验、作业加载加业务级超时，避免 loading 不收敛。
- `AiClassRepository` 为 `teachClassId` 增加 `classId` 兜底，并收紧 FIF 慢接口等待时间。
- `AiClassResponse` 将测验列表 `data` 改为 `JsonElement`，交给递归 parser 提取 `paperDetail`。
- `extractParam()` 在 `Uri` 解析为空时继续用整 URL regex 兜底，支持 hash route 中的 `jtzy`。
- 作业详情导航传参增加 `classId` 兜底。
- `AiHomeworkDetailScreen` 的提交入口和 BottomSheet 提交按钮改用 `LxButton`，Sheet 与课表 BottomSheet 的颜色 / shape 对齐。
- `AiClassRepository.getCourses()` 在 `typeName` 为空时从 `className/classNames/classNameStr` 解析 `(理论)` / `(实验)`，并把这些字段纳入课程 `stableId`，避免理论班和实验班被错误合并。

## 验证

- `.\gradlew.bat lint` 通过。
- `.\gradlew.bat test` 通过。
- 用户验证作业列表已能显示，并确认提交作业 BottomSheet 样式已调整完成。
- 用户验证 AI课堂课程理论 / 实验后缀已正确显示。
