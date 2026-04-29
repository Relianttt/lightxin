---
doc_type: decision
category: convention
date: 2026-04-22
slug: terra-color-usage-density
status: active
area: 全局 UI
tags: [designsystem, color, terra, visual-consistency]
---

## 背景

陶土色（`LxTerra`）是项目主题色，视觉辨识度最高。如果不限制使用密度，容易在全 App 各处泛滥，稀释品牌感。

## 决定

陶土色遵守以下使用密度准则：

1. **一屏最多 2 次**：首页 = 问候主标 + 横线；我的页 = "我的"标题 + 头像陶土字
2. **实心陶土 = 主 CTA**（如登录按钮）
3. **轮廓陶土 = 次级 / 确认 / destructive**（如退出登录按钮）
4. **Badge / Icon / 小标签不用陶土色**——交给墨灰或分类色

## 理由

陶土色的品牌感来自克制。用得越多辨识度越低，最后变成"又一个橙色 App"。

## 后果

- 新增 UI 时必须自查陶土色使用次数
- 按钮层级已体现在 `LxButton`（实心）/ `LxOutlinedButton`（轮廓）/ `LxSecondaryButton`（沙色）/ `LxTextButton`（文字）四级
- `LxDialog` 的 Destructive 走 `LxRose` 而非陶土

## 相关文档

- `docs/项目规划/UI改造指南.md` §9.3 陶土色使用准则
- `easysdd/architecture/designsystem-overview.md` §2.7, §6
