---
doc_type: decision
category: architecture
date: 2026-04-22
slug: route-template-json-not-room
status: active
area: 跑步路线模板
tags: [running, storage, json, room]
---

## 背景

跑步模拟需要持久化路线模板资产。早期规划文档讨论过 Room 和 JSON 文件两种存储方案。

## 决定

路线模板用 JSON 文件存储，由 `RouteTemplateStore` + `Mutex` 管理，不使用 Room 数据库。

## 理由

1. 路线模板是"资产文件"语义（整体读写、不做复杂查询），不是"结构化数据"语义
2. JSON 文件轻量，避免了 Room schema 迁移成本——这个 App 是个人项目，不想为一张表维护 migration
3. `Mutex` 保证并发安全，在当前数据量级（几十条模板）下性能无忧

## 考虑过的替代方案

- Room 数据库：支持复杂查询和结构化迁移，但模板数据不涉及关系查询，引入 Room 的维护成本不值得
- SharedPreferences：不适合存储数组/对象结构

## 后果

- 模板数据量大到数百条时，读写性能可能需要优化（当前无此风险）
- 没有数据库索引，搜索/过滤需要在内存中进行
- 模板文件通过 `Context.filesDir` 落地，卸载 App 即清除

## 相关文档

- `docs/项目规划/路线模拟实现文档.md` — 早期 Room vs JSON 讨论
- `feature/running/domain/RouteTemplateStore.kt`
- `easysdd/architecture/running-overview.md`
