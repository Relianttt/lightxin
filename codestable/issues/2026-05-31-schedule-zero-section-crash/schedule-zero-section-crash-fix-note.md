---
doc_type: issue-fix-note
issue: 2026-05-31-schedule-zero-section-crash
status: fixed
date: 2026-05-31
tags: [schedule, timetable, section-zero, compose]
---

# 课表第 0 节崩溃修复记录

## 现象

大二课表数据可能返回 `startSection=0` 的早课。旧课表网格按 1-10 节固定渲染，并用 `(startSection - 1) * CELL_HEIGHT` 计算课程块顶部偏移，遇到第 0 节会得到负偏移，导致页面布局异常或崩溃。

## 根因

`ScheduleScreen` 把课表节次起点硬编码为 1，没有把服务端返回的第 0 节作为合法显示范围处理。

## 修复

- `ScheduleScreen` 根据课程数据动态计算 `firstSection`：存在 `startSection <= 0` 时从 0 开始，否则仍从 1 开始。
- 行数由 `SECTION_COUNT - firstSection + 1` 计算；第 0 节行标签显示为“早”。
- 课程块顶部偏移改为 `(course.startSection - firstSection) * CELL_HEIGHT`，避免第 0 节出现负偏移。

## 验证

- `.\gradlew.bat :app:compileDebugKotlin` 通过。
- 代码复核确认常规 1-10 节显示路径不变，第 0 节只在数据实际出现时扩展网格。
