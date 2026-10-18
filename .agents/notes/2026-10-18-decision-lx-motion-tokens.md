---
doc_type: decision
category: convention
status: active
date: 2026-10-18
tags: [designsystem, motion, animation, compose]
---

## 背景

界面里按压反馈、颜色过渡、页面转场、骨架 shimmer 各自写死时长和曲线，观感不一致；部分动画用 `it / 4` 这类比例位移，长屏上会放大成"表演"。想借鉴 Telegram 的"运动连续性"，但它的客户端是 GPL-3.0 + Java + 自研 View 动画框架，直接搬代码会把本仓库的 MIT 传染成 GPL。

## 决定

只借"连续"的思路，用 Compose 重写，并把温度降到 Claude.ai 式：快、短、无回弹、无表演。

- 新增 `core/designsystem/theme/LxMotion.kt` 作为唯一动效 token 源；组件与页面不再各自写数字。
- 三条硬原则：
  1. **无回弹**：所有 spring 用临界阻尼 `dampingRatio = 1f`，肉眼不可见 overshoot。
  2. **短**：交互反馈 ≤ 100ms，状态变化 ≤ 200ms，页面转场 ≤ 260ms；骨架 shimmer 属于加载语义，保留。
  3. **连续而非表演**：只改位置 / 尺寸 / 透明度 / 颜色；不做视差、3D 旋转、逐项 stagger、无限装饰动画。
- 共享元素只做 bounds（位置 + 尺寸）连续性，使用 `LxSharedBoundsTransform`（临界阻尼 `spring<Rect>`）；不做文字缩放、圆角 morph。
- 允许的常驻动画只剩两处：骨架 shimmer；`PulseDot` 仅在"24 小时内截止"的紧急语义下渲染（2.6s，alpha 0.80↔0.50）。
- 参数集中在 `LxMotion` 一处调；回退粒度 = 单文件 / 单组件。

## 理由

- Telegram 的"连续"来自共享元素与手势跟手，不需要它的弹跳曲线；Claude.ai 的克制更贴合本项目的羊皮纸暖色与低饱和配色。
- GPL 代码不可搬运：本仓库是 MIT，复制 Java 动画框架会改变整个仓库的许可证义务。用 Compose 重写行为不构成衍生作品风险。
- 统一 token 后，观感问题只需改一处；组件改动不含布局，回退成本低。

## 考虑过的替代方案

- **直接搬 Telegram 客户端的动画实现**：GPL-3.0 + Java + 自研 View 动画框架，复制进 MIT 仓库会改变整个项目的许可证义务，不采用。
- **保留回弹 / 更大位移以获得"高级感"**：与本项目低饱和、羊皮纸暖色的气质冲突，且在长列表与低端机上更容易掉帧。
- **每个组件各自写时长和曲线**：实现快，但观感无法统一，调参要改十几处；已统一到 `LxMotion`。
- **完全关闭动画**：失去位置与状态的连续性，页面切换会显得生硬；保留短时连续过渡是成本最低的折中。

## 后果

- `LxCard` / `LxButton` / `LxIconButton` / `LxChoiceChip` / `LxMenuRow` 按压统一为 0.98/0.99 缩放 + 颜色 spring；`LxDialog` 入场 0.98→1（280ms）、出场 220ms，由 `Animatable` 驱动以跟随系统动画缩放。
- `LxEmpty` 的 `it / 4` 位移改为固定 6dp。
- 首页 tab 只做 6% 位移 + 淡入，stagger 最多 3 档、30ms 递增、仅首次进入播放。
- 系统"动画时长 0 / 移除动画"下由 Compose durationScale 直接落终值，不留中间态。

## 相关文档

- `codestable/architecture/designsystem-overview.md`
- `codestable/architecture/navigation-overview.md`
- 代码：`core/designsystem/theme/LxMotion.kt`、`core/designsystem/component/LxSharedTransition.kt`

## 后续动效修订

本篇的“快、短”和时长上限由用户要求放慢，参见 [舒缓分页动效](2026-10-18-decision-gentle-tab-paging.md)。无回弹、统一 token、无装饰动画及系统动画缩放约束继续有效。
