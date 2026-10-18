---
doc_type: learning
track: pitfall
status: active
date: 2026-10-18
tags: [compose, bottom-sheet, modal-bottom-sheet, design-system, ui]
---

## 现象

新建报修的「选择楼栋」弹层直接顶满全屏，列表在一屏末尾被截断且上滑无位移（前后截图字节一致），
用户表现为"弹层占满屏幕 + 数据不全（其实是被裁掉了）"。选项 36 项，来自
`bxjlSjd/getBxddForSjd.do`。

## 根因

`ModalBottomSheet` 的高度由内容决定，内部不会自动给内容加滚动：

- `LxOptionSheet` 用普通 `Column` 平铺选项 → 内容多高 sheet 就多高，直接铺满屏幕；
- `Column` 里的子项超出 sheet 边界被裁剪，且没有滚动容器，末尾内容永远够不到。

对用户来说"看不到"和"没有数据"是同一种体感，别把这类反馈直接当成接口问题。

## 解法

- 选项类弹层一律 `LazyColumn` + `heightIn(max = …)`（本项目取屏幕高度 0.42、夹 220–360dp，
  约 7 行），让列表内部滚动、弹层保持底部卡片形态；
- 长列表在标题行显示「共 N 项」，把"总数"暴露出来，快速区分"没拉到"和"没滚到"；
- 空态补一句文案，避免只剩一个标题的空弹层。

## 预防

- 设计系统里凡是"选项/列表来自服务端且条数不定"的弹层，默认按"可能几十条"设计，先加滚动和限高。
- 验证 UI 时，滑不动就是真滑不动：连续两次相同截图的 sha256 一样，即可判定没有滚动容器，
  不用猜是不是手势位置不对。
- 同类问题此前出现过一次（`codestable/issues/2026-05-19-credit-exam-ui-polish` 的
  BottomSheet 全屏回落），属于第二次踩坑，按项目约定沉淀为本 note。
