---
doc_type: decision
category: convention
status: active
date: 2026-10-25
tags: [motion, compose, designsystem]
---

## 背景

用户确认 UI 与动效 v3 报告，目标是细腻而非更快。单一曲线、同速进退与固定 tween 分页无法满足这次要求。本篇部分修订 [动效 token](2026-10-18-decision-lx-motion-tokens.md) 与 [跟手分页](2026-10-18-decision-gentle-tab-paging.md)。

## 决定

- 进场长减速、退场加速、屏内往返对称；透明度、小位移与小缩放组合，容器先于内容出现，进场慢于退场。
- 转场不 overshoot；唯一轻回弹点为按压松开，`PressRelease` 阻尼 0.75，可独立回退为 1。
- 固定时长除计数外不超过 450ms；手势驱动物理动画不受此限。系统边缘拉伸保留，不自定义 overscroll，不手写底部弹层运动。
- 所有参数归 `LxMotion`；Compose durationScale 关闭动画时直接落终值，不用固定 delay 代替动画结束。
- 底部 tab 淡出后淡入并轻缩放，不横移；跟手 Pager 使用临界阻尼弹簧吸附。列表行只变暗，按钮变暗并轻缩放，文字动作仅降低文字 alpha。

### Token 表

| 类别 | 值 |
|---|---|
| 曲线 | Enter=(0.22,1,0.36,1)，Exit=(0.4,0,1,1)，InOut=(0.4,0,0.2,1) |
| 按压 | In=90ms，Out=200ms，Button=0.97，Card=0.985，OverlayAlpha=0.05 |
| 状态 / 内容 | State=220ms，StateExit=160ms，Enter=400ms，Shift=10dp，ContentDelay=60ms |
| 页面 | Enter=400ms，FadeIn=280ms，FadeDelay=80ms，FadeOut=120ms，Exit=240ms，Shift=8%，反向退=4% |
| tab | FadeOut=90ms，FadeIn=220ms，InitialScale=0.98 |
| 浮层 | Enter=320ms，Exit=180ms，InitialScale=0.96，ExitScale=0.98 |
| 首屏编排预留 | Stagger=30ms，MaxItems=5，Window=600ms，CountUp=700ms |
| 首页卡片 | Enter=350ms，Stagger=60ms，Shift=0.3333（保留用户指定数值） |
| 加载 / 引导 | Skeleton=2200ms，Shimmer=1200ms，Pulse=2600ms，Onboarding=450ms，错峰基数/步长=80/90ms，Shift=0.25 |
| 弹簧 | Move=380，SharedBounds=300，PressIn=(1,1500)，PressRelease=(0.75,500)，Color=(1,600) |
| 其他 | QuickFade=160ms，Collapse=180ms，Banner/Delay=200/200ms |

## 考虑过的替代方案

- 单纯延长时长：可放慢但不能区分进退场节奏，改为独立曲线和属性组合。
- 420ms tween 吸附：固定时长易调，但本次改为承接手势速度的临界阻尼弹簧。
- 所有反馈缩放：整行缩放过于明显，行只变暗，文字动作仅改透明度。

## 后果

P2 基础动效已通过单元测试、lintDebug、Debug 与 androidTest APK 构建，并保留数据覆盖安装；首页/课表/我的及模块导航已完成真机冒烟。尚未完成 release 三次 fling 对照与录屏，不能作为回弹诊断或手感验收结论。

P7 已接线首屏错峰（`lxStaggerEnter`）、列表增删 `animateItem`、计数与分段填充（`rememberLxCountUp`）、点击/分页吸附/下拉阈值/开关/签到结果触感，以及预测性返回（`enableOnBackInvokedCallback`）。共享容器/圆角过渡不再被原则性禁止，但 T12-C 仍需先向用户展示录屏并确认，当前未实施。
