---
doc_type: decision
category: convention
date: 2026-04-22
slug: feature-tri-layer-package
status: active
area: 全局代码结构
tags: [architecture, package, clean-architecture]
---

## 背景

项目采用 Clean Architecture 思路，feature 包内的代码分层方式需要在项目初期确定。

## 决定

feature 代码按 `data / domain / ui` 三层分包，不按层分包。每个 feature 是一个独立包（如 `feature/login/`），内部再按 `data/`、`domain/`、`ui/` 组织。

## 理由

1. 按 feature 分包让每个功能自包含，新增/删除 feature 时不影响其他功能
2. 同一 feature 的 data / domain / UI 代码物理距离近，修改时不用跨多个顶层目录
3. 按层分包（所有 Repository 放一起、所有 ViewModel 放一起）在 feature 数量增长后会导致单个目录膨胀

## 考虑过的替代方案

- 按层分包（`repositories/` / `viewmodels/` / `screens/`）：在大型团队+多模块项目中更常见，但本项目 feature 数量有限，按 feature 分包更直观

## 后果

- `core/` 放跨 feature 的基础设施，`feature/` 放业务功能，边界清晰
- 新增 feature 时复制 `data / domain / ui` 三层骨架
- `core/` 内部不强制三层（如 `core/network/` 按职责组织而非 data/domain/ui）

## 相关文档

- `AGENTS.md` — 项目结构说明
- `codestable/architecture/DESIGN.md` §2, §3
