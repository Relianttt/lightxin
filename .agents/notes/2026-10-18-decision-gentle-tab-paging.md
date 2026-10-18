---
doc_type: decision
category: convention
status: active
date: 2026-10-18
tags: [designsystem, motion, pager, leave, repair, checkin]
---

## 背景

用户反馈全局动画太快，希望更慢、更细腻；学工在线、请假、报修左右切换缺少滑动过程，请假每次切页又出现加载。手势阈值回调只在松手后替换列表，请假把切页绑定为清空并重拉，无法提供连续性。

## 决定

- 保留现有视觉语言、无回弹与系统动画缩放支持；按下仍及时响应，状态过渡 280ms，页面进入 420ms、退出 350ms，分页吸附、点选与首页底部 tab 均 420ms。统一参数仍由 `LxMotion` 持有。
- 三个页面共用 `LxTabPager`：拖动期间跟手，松手后吸附；点 tab 播同一方向的横向过渡，邻页保留，各页拥有独立的纵向滚动位置。标题与 tab 行固定在 pager 外（Scaffold `topBar`），切页时只有列表内容横移；默认仍是查寝 / 进行中，空页不自动切换。
- tab 选中态跟随 `currentPage + currentPageOffsetFraction` 连续过渡（`LxFilterTabs.indicatorFraction`），不再用 `targetPage` 在滑动过半瞬切；tab 行加 `selectableGroup()` 供 TalkBack 成组播报。tab 项关闭默认 ripple（无 contentColor 时为黑色）、1dp 阴影与描边，选中块只用白色背景按拖动比例连续插值。
- `LxDialog` 用单个 `Animatable` 驱动透明度与缩放，出场 220ms（`DialogExitMs`）播完再回调；不再用 raw `delay`，系统关闭动画时立即关闭。
- 请假在列表 ViewModel 生命周期内保留两份独立内存列表，首次并行取两页首屏，不为 tab 选择发请求。总数仍以服务端 `total` 为准；进行中只有加载完整后才扣除已撤回并显示计数。
- 下拉刷新、提交 / 撤回 / 销假返回触发两页刷新，保留旧内容直到响应；失败可重试。取消请求即使被 repository 包装为失败，旧响应也不能覆盖刷新结果。翻页页码、原始行集合和错误状态按 tab 隔离。
- 缓存不落盘、不做后台轮询，不改学校接口字段、鉴权或加密。

## 考虑过的替代方案

- 只延长时长：改动最小，但无法补上拖动时的位置反馈，也无法消除切页重拉。
- 切页时才加载并只缓存当前 tab：少一次首屏请求，但首次滑动仍容易看到骨架；现有另一 tab 计数本身就需要请求，改为同时取可展示首屏。
- 明显弹跳 / overshoot：存在感强，但用户对回弹的追加要求以 Claude.ai 确实使用为前提，尚未确认该事实；不据此推翻无回弹约束。

## 后果

- 首次网络未完成时仍有真实加载态；已加载的页来回滑不再重复请求。新开列表和主动刷新仍会联网，不承诺永久离线。
- 两页列表和邻页组合驻留内存，换取连续滑动和滚动位置保留；新数据与计数可能在刷新完成前暂时显示旧值。
- 修订 [动效 token 决策](2026-10-18-decision-lx-motion-tokens.md) 中的短时长上限，以及 [移动优先 UI 修订](2026-10-18-decision-mobile-first-ui-revision.md) 中的 56dp 松手切换机制；其余决定保持有效。

## 验证入口

- `LeaveListStoreTest`：双页并发、缓存读取不重拉、分页含撤回计数、刷新保留、失败隔离、过期响应。
- `TabPagerInstrumentedTest`：松手前内容移动、短拖回位、双向滑动与边界、点 tab、独立滚动位置。
- `TabSelectionWeightTest`：指示器权重连续、单调、只在过半跨 0.5。
- `MotionTokenGateTest`：动画时长只允许来自 `LxMotion`（白名单 `LxMotion.kt`、`*Illustration.kt`）。
