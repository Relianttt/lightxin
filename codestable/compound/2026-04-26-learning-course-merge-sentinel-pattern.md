---
doc_type: learning
track: knowledge
severity: medium
component: [aiclass, repository]
title: 多源课程列表归并的常见陷阱与哨兵 stableId 跨页面传值模式
date: 2026-04-26
tags: [aiclass, course-merge, typeName, stableId, viewmodel, sentinel]
---

# 多源课程列表归并 + 哨兵 stableId 模式

## 背景

AI课堂课程列表有两个数据源：
- `myClassroom`（主）— 返回当前学期课程，但部分课被服务端条件性隐藏
- `timetableInfo`（补）— 课表接口，用于补漏

两者归并时需要按 ID 或名称匹配，并合并字段。同时，"正在上课"卡片对应的课程可能不在主列表中，需要一个"临时 AiCourse"来承载详情页展示。

## 归并的四个典型陷阱

### 1. 课程名含后缀导致同名无法匹配

**现象**：课表返回 `courseName = "智能网联汽车电子技术（实验）"`，主列表返回 `"智能网联汽车电子技术"`，normalize 后（仅去空格）仍不相等 → 匹配失败 → 被当成新课追加 → 重复条目。

**修复**：`normalizedCourseName()` 先去空格，再去掉 `（实验）`/`（理论）`/`(实验)`/`(理论)` 等后缀再比较。

### 2. 身份匹配忽略 typeName 冲突

**现象**：主列表课程无 typeName，先被课表"理论"版匹配合并（typeName="理论"）；课表"实验"版过来时，teachClassId 不同但共享其他 ID → `matchesByIdentity` 返回 true → 被吸收但 typeName 保持"理论" → 实验版消失。

**修复**：`matchesByIdentity()` 末尾加判断：两个课程 typeName 都有值且不同时，返回 false，视为不同条目。

### 3. 课表 JSON 字段名覆盖不足

**现象**：`toTimetableCourseOrNull` 只查 `classId`/`classIds` 和 `courseName`/`courseItemName`。课表用 `teachClassId`/`id`/`name`/`title` 等其他字段名时，直接返回 null 丢弃。

**修复**：`stringValue()` 调用补上常见 fallback 键：`classId, classIds, teachClassId, id` / `courseName, courseItemName, name, title` / `teacherName, teacher`。

### 4. 同 teachClassId 的多班次互斥

**现象**：`extractTimetableCourses` 用 `putIfAbsent(teachClassId)` 去重。同一 teachClassId 下有理论/实验两个条目时，后者被丢弃。

**修复**：dedup key 带上 typeName：`"$baseKey|$typeName"`，理论/实验各占一条。

## 哨兵 stableId 模式：临时模型跨页面传值

当源数据（如 `AiWorkingRecord`）不能直接作为详情页的 `AiCourse` 使用时：

```kotlin
fun openWorkingRecordDetail() {
    val record = _uiState.value.workingRecord ?: return
    // 1. 在已有列表中反查，有就用（保留 typeName 等字段）
    val existing = _uiState.value.courses.find { it.teachClassId == record.teachClassId }
    // 2. 构建临时模型，stableId 统一用哨兵值
    val base = existing ?: AiCourse(/* 从 record 映射 */)
    val course = base.copy(stableId = "_working")
    // 3. 设置状态 + 加载数据
    _uiState.update { it.copy(selectedCourse = course, isQuizLoading = true, ...) }
    // 4. 协程内陈旧数据防护
    viewModelScope.launch {
        val result = repository.getQuizList(record.teachClassId)
        _uiState.update { current ->
            if (current.selectedCourse?.stableId != "_working") return@update current
            current.copy(quizList = result.getOrDefault(emptyList()), ...)
        }
    }
}
```

要点：
- **stableId 统一为哨兵值**：无论是否在主列表找到匹配课程，哨兵值不变，协程防护只检查它
- **`isQuizLoading = true` 必须设**：否则详情页在加载完成前会闪现"暂无测验"
- **LaunchedEffect 兼容**：`openCourseDetail("_working")` 遇哨兵直接 return，不重复加载
- **导航用 `launchSingleTop`**：防止快速双击产生重复页面
