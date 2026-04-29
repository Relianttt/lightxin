---
doc_type: decision
category: architecture
date: 2026-04-22
slug: triple-typeface-cjk-fallback
status: active
area: 全局字体
tags: [designsystem, typography, cjk, fallback]
---

## 背景

项目使用衬线字体（Newsreader）做标题来建立品牌气质，但 Newsreader 只覆盖拉丁字形。中文字符会 fallback 到系统字体（通常是黑体），导致衬线风格断裂。

## 决定

采用三字族策略：Newsreader（拉丁衬线）+ Outfit（sans 正文/数字）+ Noto Serif SC（中文衬线 fallback）。Noto Serif SC 只使用 Regular 字重，同一 TTF 注册为 `FontWeight.Normal` + `FontWeight.Medium` 两条 Font 项。

## 理由

1. 中文字笔画密度远高于拉丁，Regular 中文配 Medium Newsreader 才视觉对齐
2. 两边都 Medium 会让中文"黑一块"，笔画糊在一起
3. 同一 TTF 注册两条 Font 项是 Compose FontFamily 的标准做法，无运行时开销

## 考虑过的替代方案

- Noto Serif SC 也加 Medium 字重：中文笔画密度下效果差，视觉不协调
- 不加中文 fallback、让系统黑体兜底：首页标题会出现"衬线拉丁 + 黑体中文"的割裂感

## 后果

- 所有 Newsreader 字族的 FontFamily 链必须包含 Noto Serif SC Regular
- UI 层使用衬线标题时无需手动指定中文字体，FontFamily 链自动 fallback
- 未来如果更换中文字体，需同步更新三个 Newsreader 字族

## 相关文档

- `docs/项目规划/UI改造指南.md` §9 Phase 9C 字体 fallback
- `core/designsystem/theme/Type.kt:11-14,20-45`
- `easysdd/architecture/designsystem-overview.md` §2.5
