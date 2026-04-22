---
doc_type: decision
category: architecture
date: 2026-04-22
slug: home-narrative-structure
status: active
area: 首页
tags: [home, narrative, dashboard, scene]
---

## 背景

Phase 9A 之前首页是传统 4 卡 dashboard（课程卡 + 查寝卡 + 跑步卡 + 劳动卡）。这种布局信息密度高但不聚焦，用户打开 App 时最想知道的是"此刻我该做什么"。

## 决定

首页从"4 卡 dashboard"改为"此刻我该做什么"叙事结构。具体改动：

1. 删除劳动卡
2. 新增"下一节" headline（`NextClassHeadline`）
3. 查寝卡片条件渲染（开始前后 4 小时窗口）
4. 运动卡删总进度条
5. 场景判定由 `HomeScene` 枚举族 + `SceneResolver` 纯函数承载
6. 副标题文案库按日期种子轮换

## 理由

1. 校园 App 的核心使用场景是"此刻我需要做什么"，不是"一览所有信息"
2. 叙事结构让首页首屏聚焦于最相关的单一任务
3. 场景判定从 UI 层 if/else 提取为独立纯函数，可测试且不和 Compose 绑定

## 考虑过的替代方案

- 保持 4 卡 dashboard：信息密度高但不聚焦
- 设计稿方案"单智慧卡片状态机"：当前尚未完整落地，UI 仍是固定骨架 + 条件插卡

## 后果

- 首页不再是"所有功能入口"的 dashboard，而是以"当前任务"为中心的叙事页
- 劳教数据仍然加载但不在首页渲染（留给"我的"页）
- 后续如果要做设计稿中的"双态布局"（空态 / 单智慧卡片），需要在此基础上继续重构

## 相关文档

- `docs/项目规划/homepage-design-spec.md` — 原始设计意图
- `easysdd/architecture/home-overview.md` — 当前实现状态
- `feature/home/domain/HomeScene.kt` — 场景枚举族
- `feature/home/domain/SceneResolver.kt` — 纯函数场景判定
