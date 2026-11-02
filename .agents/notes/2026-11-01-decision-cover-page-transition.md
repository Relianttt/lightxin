---
doc_type: decision
category: convention
status: active
date: 2026-11-01
tags: [motion, navigation, compose, designsystem]
---

## 背景

用户录屏反馈页面切换「怪」且掉帧。逐帧与 perfetto 分析：

- 旧页淡出 120ms 与新页延迟 80ms 淡入重叠，两页同时半透明，文字叠印；`EaseEnter` 前段过重，位移在新页还看不见时就走完，400ms 的转场肉眼只剩约 100ms。
- `HOME` 单独写了 `fadeIn/fadeOut`。`composable()` 的 `popEnter/popExit` 默认取 `enter/exit`，所以这个淡入淡出也作用于所有返回首页和从首页进入的转场，底部 tab 栏跟着页面一起淡出。
- 新页首次组合/测量常有 30–110ms 长帧，正好落在可见窗口内。
- 返回手势期间页面不动，松手才一下播完。

本篇部分取代 [动效 v3](2026-10-25-decision-motion-v3.md) 中的页面转场与首页底部 tab 规则，其余 v3 规则不变。

## 决定

- **页面层级用覆盖式（参考 iOS）**：上层页整屏宽滑入/滑出，全程不改透明度；下层页同向视差 `PageUnderShiftFraction=0.25`。进退都用 `LxMotion.pageSlide()`（临界阻尼，stiffness `PageStiffness=380`）。层级由 NavHost 的 zIndex 管：push 时新页在上，pop 时被返回页在上。
- **页面根容器必须不透明**（`Scaffold` 默认 `background` 即可），否则下层页会透出来。
- **路由不单独覆盖转场**，统一走 `LightXinNavHost` 的全局转场；确需覆盖时记住 `popEnter/popExit` 会默认继承。根切换是例外：旧页一律 `KeepUntilTransitionsFinished` 原地不动，新页（首页 / 登录页）淡入盖上，不让两页同时淡出淡入。`NavHost` 的 `startDestination` 只取首次解析结果并冻结——它一变 `setGraph` 就清栈重建，登录后会多压一个首页，与淡入中的首页叠印。
- **返回手势沿用 NavHost 内置的预测性返回**：navigation-compose 2.9.x 用 `SeekableTransitionState.seekTo(progress)` 驱动 `popEnter/popExit`，不自建 `AnimatedContent`。手势期间返回栈还没出栈（被返回页仍是 `currentBackStackEntry`），据此在 `popSlideSpec` 里区分手势与按钮返回：
  - 手势用 `tween(PageGestureMs=350, PageGestureEasing)`：前 20% 与手指 1:1，之后接三次 Hermite（起点斜率 1、终点斜率 0、最大斜率 4/3）。松手后 NavHost 沿这条曲线按剩余比例实时播完，表现为先加速再减速落定。
  - 按钮/系统返回已出栈，用 `pageSlide()` 弹簧。
- **首页底部 tab 按顺序左右整屏推移**，弹簧与页面相同，两页并排不重叠。
- **tab 内容每次切换都会重建**：只该播一次的入场（首页卡片）把「已播过」标记放在 tab 外层的 `rememberSaveable`；首次定位类滚动（课表周次条）首次用 `scrollToItem` 直接落位，之后再动画。

### Token

| 类别 | 值 |
|---|---|
| 页面 / 首页 tab | `pageSlide()`：dampingRatio=1，stiffness=380，IntOffset 可见阈值 |
| 下层视差 | 0.25 屏宽 |
| 返回手势 | 350ms，`PageGestureEasing`（1:1 段到 0.2） |

## 考虑过的替代方案

- **Material shared axis / 淡出后淡入**：Android 原生规范，位移小，对整屏位移的掉帧不敏感。但它靠时间错开来避免叠印，要求头几帧不掉；实测新页首帧常有长帧，一掉帧就会跳到空白或直接跳到终态。覆盖式不改透明度，掉帧时位置仍然连续，还少了两个整页离屏透明层。
- **首页 tab 直接切换（iOS 标签栏）**：最省、不受首帧影响。真机试过后用户认为没有动画太生硬，否决。
- **首页 tab 保留淡出淡入只缩短时长**：用户要求改为平移，否决。
- **返回手势全程线性**：完全跟手，但松手后匀速走完剩余部分，用户认为缺少加速度。
- **自建 `PredictiveBackHandler` + 转场，按松手速度做 iOS 式承接弹簧**：手感最接近 iOS，但要复刻 NavHost 的 visibleEntries、生命周期与 `onTransitionComplete` 逻辑。目前用固定曲线近似，加速幅度不随手速变化。

## 后果

- 两页不再叠印，tab 栏随首页一起被覆盖；用户在 MEIZU 20 上用 release 版确认了页面转场与 tab 手感。
- 整屏位移对首帧长帧更敏感，首帧慢会表现为页面跳一截。首帧开销还没排查（Compose runtime-tracing 定位到具体页面、Baseline Profile、14.8MB 衬线字体对文字测量的影响）。
- 弹簧到 1px 阈值约 480ms 才结束，新页 `RESUMED` 与 `onTransitionComplete` 相应推迟；视觉上约 350ms 落定。
- 手势跟手依赖系统下发进度：需要 API 34+。Flyme 的全局设置 `enable_back_animation=0` 时疑似不下发进度（未验证），Pixel 4（Android 13）也不支持，这两种情况都退化为按钮式弹簧返回。跟手与松手曲线尚未真机验证。
- 没有边缘阴影和下层压暗，需要时再加。
