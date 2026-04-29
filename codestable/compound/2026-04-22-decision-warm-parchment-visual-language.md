---
doc_type: decision
category: architecture
date: 2026-04-22
slug: warm-parchment-visual-language
status: active
area: 全局 UI
tags: [designsystem, theme, color, visual-language]
---

## 背景

项目初始使用 Material 默认蓝橙色板，与轻小信"轻量、舒适"的品牌定位不符。Phase 9A 需要确立一套统一的视觉语言。

## 决定

采用 Anthropic 暖色 / 羊皮纸质感视觉语言：陶土主题色 + 沙色填充 + 四级墨灰文本阶梯，替换原有 Material 蓝橙色板。

## 理由

1. 原型 `prototype/anthropic-redesign.html` 验证了暖色调在校园场景下的可读性和品牌辨识度
2. 羊皮纸底色（`#F5F0E8`）在长时间阅读下比纯白更舒适
3. 陶土色（`#C4704B`）作为主色调有足够的辨识度，又不会像纯橙那样刺眼

## 考虑过的替代方案

- 保持 Material 默认色板：辨识度不够，和原 APP 视觉无差异
- 纯白底 + 蓝色强调：太"工具化"，不符合品牌定位

## 后果

- 色板、字体、圆角三层 token 全部重写（Phase 9A 一次性落地）
- 所有通用组件需要逐个适配新 token
- 后续新增 UI 必须遵守暖色体系，不能混入 Material 默认色

## 相关文档

- `docs/项目规划/UI改造指南.md` §1 设计 Token
- `easysdd/architecture/designsystem-overview.md` §2.1-2.2
