---
doc_type: learning
track: pitfall
date: 2026-05-13
slug: flyme-shortcut-launch-transition
component: [shortcut, splash, navigation, rom]
severity: low
tags: [flyme, shortcut, splashscreen-api, quickstep-launch-app, system-ui, rom-quirk, task-transition, libchecker]
---

# Flyme 上 shortcut 启动时的对角平移动画无解

## 问题

`app-shortcut-checkin` feature 实现完成后，在 Flyme 设备上从桌面长按图标 → 点击 shortcut 进入 app 时，splash 显示前后会出现一段明显的**由左向右**（早期观察以为是左上→右下对角线）的全屏遮罩平移动画，约 300-500ms，视觉嘈杂。从普通桌面图标进入则没有这个动画。

本次为修这个动画花了大半天，绕过四五条死胡同后定位到根因是 OEM SystemUI 行为，应用层无法干预，最终选择接受。本文把全部弯路写下来，避免下次再陷进去，也给后人留一份可参考的入手点（如果有人想再尝试）。

## 症状

不同启动路径在 Flyme 上的表现：

| 入口 | 对角动画 | 解读 |
|---|---|---|
| 普通桌面图标冷启动 | 无 | launcher → app 默认 transition 简单 |
| 普通桌面图标后台点击 | 无 | task reorder to front，无新 transition |
| Recents 任务列表切回 | 无 | 同上 |
| **长按图标的 shortcut（冷启动）** | **有** | 走特殊 task transition |
| **长按图标的 shortcut（后台）** | **有** | 同上 |

跨 ROM 对比：

- **Flyme**：shortcut 启动时由左向右平移
- **联想平板**（另一台 ROM）：**没有 splash**但有相同的对角动画——证明动画与 splash 显示与否解耦
- **类原生 ROM**：未实测，按 LibChecker 在 Flyme 上无此问题推断，类原生大概率也无此问题

辅助证据（关键 logcat）：

```
V/WindowManagerShell: onTransitionReady (#10492) ... QuickstepLaunchApp
W/PerfCore: perf hint acquire release scene = splashscreen_exit_anim not exist
```

`QuickstepLaunchApp` 是 Android 12+ SystemUI / WindowManagerShell 用的 launcher → app **启动 transition handler**，基于 Launcher3 / Quickstep 框架。Flyme 把它在 shortcut intent 路径上的视觉自定义成对角平移。

`splashscreen_exit_anim not exist` 是 Flyme PerfCore 的私有性能 hint 查询，**和这个动画的因果关系没有证据支持**——曾被一度误读为根因，详见下面"没用的做法"。

## 没用的做法

诊断顺序按时间线写。每条都附"当时的假设"和"为什么不成立"。

### 1. `setOnExitAnimationListener { provider.remove() }`

**假设**：以为是 Android 12+ SplashScreen API 的退出动画，listener 内立即 remove view 跳过默认动画。

**结果**：冷启动普通图标的 splash 退出动画确实被压制（侧面证明 listener 起作用），但 **shortcut 路径的对角动画还在**。

**为什么不成立**：splash exit 阶段发生在 SystemUI task transition 之**后**——SystemUI 的对角动画在 app 进程都还没起来时就已经播完，listener 注册时机太晚。

### 2. shortcut XML 加 `android:flags="0x10000"`（`FLAG_ACTIVITY_NO_ANIMATION`）

**假设**：shortcut intent 默认带 `NEW_TASK + CLEAR_TASK`，触发 task transition；给 intent 加 `NO_ANIMATION` 让系统跳过转场。

**结果**：**编译失败**。

```
xml/shortcuts.xml:13: error: attribute android:flags not found.
```

**为什么不成立**：静态 shortcut 的 `<intent>` 标签是受限 schema，只支持 `action / targetPackage / targetClass` 和 `<extra>` 子标签。`android:flags` 在该 schema 里**不存在**。这是与 `<intent-filter>` / `<intent>`（在 manifest 外的）不同的解析器。

### 3. `launchMode="singleTask"`

**假设**：standard launchMode 下 shortcut intent 重建 Activity，让 SystemUI 走完整 launcher → app 动画。改 singleTask 后 Activity 实例唯一，shortcut 热启动走 `onNewIntent` 不重建，可能避开 transition。

**结果**：对角动画**仍在**。

**为什么不成立**：SystemUI 的 task transition 选 handler 是基于 intent 路径（是否 launcher 启动、是否 NEW_TASK 等），与 Activity 实例化方式无关。Activity 不重建只省了 `onCreate`，省不了 SystemUI 那一帧。

**注意**：这条改动**最终保留了**，理由独立——它落实了 design Step 2 的"热启动 onNewIntent 更新 pending target"假设，对单 Activity Compose 架构是更合理的 launchMode。和本对角动画问题无关。

### 4. `overrideActivityTransition` / `overridePendingTransition`

**假设**：被启动方 Activity 禁用入场动画。API 34+ 的 `overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)` 官方支持被启动方调用。

**结果**：Flyme 上无效。

**为什么不成立**：这些 API 控制的是 framework Activity transition（应用进程层），不影响 SystemUI 进程的 `QuickstepLaunchApp` transition handler。两者在不同进程、不同动画体系。

### 5. Compose Navigation `enterTransition / exitTransition = EnterTransition.None`

**假设**：用户慢速观察发现动画是"由左向右平移"——这正好匹配 `NavGraph.kt` 默认 `slideInHorizontally { it / 4 }`。shortcut 链式 navigate（HOME → AICLASS_HOME → AICLASS_SCAN）每步播 slide，叠加 600-900ms。

**先试 B 方案**：用 `mutableState` 标志 shortcut 期间，enterTransition 返回 None。改完仍有动画。

**再试诊断版**：NavHost 所有 transition 改成无条件 `EnterTransition.None / ExitTransition.None`。对角动画**仍然存在**。这一步实锤了根因不在 Compose Navigation。

**为什么不成立**：用户观察到的"由左向右"在视觉时间线上与 Compose Navigation 的 slide 看起来相似，但其实是 SystemUI 层的 transition——发生在 Compose 内容渲染之前。Compose Nav 改成 None 只影响 splash 退出之后的 Compose 内部跳转，看不到也压不住 SystemUI 那一段。

### 6. 自定义 splash exit anim（未实施）

**曾经的推测**：`splashscreen_exit_anim not exist` 提示 Flyme 找不到自定义 splash exit 资源，所以走默认对角 transition。如果设置 `windowSplashScreenAnimationDuration` + `windowSplashScreenAnimatedIcon`，或者 listener 里主动播自定义动画，Flyme 可能让出 transition。

**为什么放弃**：经过 logcat 完整还原后认定时间线上对角动画发生在 splash 显示之**前**（SystemUI 阶段），splash exit anim 无论怎么配都干预不了那个阶段。`splashscreen_exit_anim not exist` 这条 PerfCore log 与对角动画的因果关系**没有证据支持**，是被过度解读了一次。

## 解法

**没有应用层解法。接受为 Flyme ROM 已知行为。**

回滚所有诊断改动到无效的部分；保留 `MainActivity` 的 `launchMode="singleTask"`（独立于本问题的合理改动）。具体见"代码锚点"。

## 为什么应用层无解

`QuickstepLaunchApp` 是 Android 12+ 引入的 SystemUI 启动动画处理器：

```
点 shortcut
   ↓
launcher 发出 intent（NEW_TASK + CLEAR_TASK）
   ↓
SystemUI WindowManagerShell 选 transition handler → QuickstepLaunchApp
   ↓ ←── 对角平移在这一刻播，OEM 自定义视觉
splash window 显示
   ↓
MainActivity onCreate → Compose 渲染
   ↓
splash exit
```

- 这个动画**在 SystemUI 进程**，不在 app 进程
- 触发**在 app 进程启动之前**，连 `onCreate` 都没跑
- OEM（Flyme）只在 shortcut intent 路径（intent 带 `FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TASK` 的组合）给这个 handler 配特殊视觉
- 普通 `ACTION_MAIN + CATEGORY_LAUNCHER` 路径走另一条 transition，OEM 没特殊处理，所以无遮罩
- 应用层（包括 manifest、theme、launchMode、SplashScreen API、Activity transition、Compose Nav）全都在 SystemUI 之**后**或之**外**生效

## LibChecker 对照

LibChecker（同类 Android App，在 Flyme 上同样有 shortcut，但**无此动画**）的关键差异：

| 维度 | LibChecker | lightxin |
|---|---|---|
| **SplashScreen API** | **不用**（`installSplashScreen()` grep 0 命中） | 用 `installSplashScreen()` |
| MainActivity 主题 | `AppTheme`（Material 普通主题） | `Theme.LightXin.Splash`（继承 `Theme.SplashScreen`） |
| launchMode | `singleTop` | 现 `singleTask` |
| shortcut intent | 静态 XML，custom action | 静态 XML，custom action |
| shortcut 目标 | 一个 `ChartActivity`、两个 `MainActivity` | 都 `MainActivity` |

LibChecker manifest 来源：[`app/src/main/AndroidManifest.xml@master`](https://github.com/LibChecker/LibChecker/blob/master/app/src/main/AndroidManifest.xml)。
LibChecker shortcuts 来源：[`app/src/main/res/xml/shortcuts.xml@master`](https://github.com/LibChecker/LibChecker/blob/master/app/src/main/res/xml/shortcuts.xml)。

**最可能的根因**：Flyme 的 `QuickstepLaunchApp` 对角动画**专门作用于 SplashScreen API 触发的 splash window**。LibChecker 因为没用 SplashScreen API，系统只能 fallback 用 `windowBackground` 显示一个简单 splash 视觉，SystemUI 不把它当作"标准 splash" → 不挂对角动画。

`launchMode` 差异（singleTop vs singleTask）已经在本次测试中排除——改 singleTask 无效。所以**决定性差异是 SplashScreen API 是否启用**。

但这只是 hypothesis，没在 lightxin 上跑过对照验证。

## 未来修复方向（如果回来再尝试）

按估计可行性排序：

### 方向 A：拆 SplashScreen API（最有希望）

仿 LibChecker：

1. `themes.xml` 的 `Theme.LightXin.Splash` 不再继承 `Theme.SplashScreen`，改成普通 `NoActionBar` + `windowBackground = @drawable/splash_window_background`（layer-list：羊皮纸底 + 中心 logo）
2. `MainActivity.kt` 删 `installSplashScreen()` 及 `setKeepOnScreenCondition` 协调
3. splash 等待逻辑（`HomeBootstrap.ready` + 1500ms 超时 + 查寝预解析）挪到 Compose 内，用 `LxLoading` 或全屏 logo 占位

**代价**：失去 Android 12+ 官方 splash 体验（图标缩放动画）、与 Google 推荐方向逆行、需重写 splash 协调约 30 行、不能 100% 保证 Flyme 真的让出 transition。

### 方向 B：动态 shortcut + `FLAG_ACTIVITY_NO_ANIMATION`

`ShortcutManagerCompat.setDynamicShortcuts()` 注册同 ID 的动态 shortcut 覆盖静态版，构造 intent 时加 `FLAG_ACTIVITY_NO_ANIMATION`（`0x10000`）。

**代价**：增加 shortcut 注册时机（首次启动后），launcher 缓存与刷新需要小心。Flyme 不一定尊重 framework flag（它的 SystemUI handler 可能直接无视）。

### 方向 C：跑 ActivityOptions 自定义 launch animation

在某个对外可触发 shortcut 的入口（如果有）用 `ActivityOptions.makeCustomAnimation()` 替代默认 transition。但 launcher 调起的 shortcut 不经过 app 代码，这条对 launcher 启动场景无效，只对 app 内自启场景有效。可能 0 收益。

### 方向 D：动手调 SystemUI（不可行）

需要 root + 自编译 Flyme SystemUI / Launcher3。明显出 app 开发范围。

---

**任何一条都不保证 100% 生效**。Flyme 的 ROM 实现细节闭源，跨版本可能不一致。如果将来 Flyme 自己迭代过、或者目标 ROM 范围变化，问题可能自然消失或加剧。

## 预防

下次遇到"启动动画异常"或类似 OEM ROM 视觉问题：

1. **先用 adb logcat 看 transition handler**，命令：

   ```bash
   adb logcat -c
   # 触发动画
   adb logcat -d -v time | grep -iE "transition|animator|splash|wm_|wmshell"
   ```

   `onTransitionReady (#NNNN) ... XxxHandler` 是关键——能直接告诉你动画归哪一层管。

2. **录屏 + 慢放**比靠脑补可靠。`adb shell screenrecord --bit-rate 16000000 --time-limit 5 /sdcard/x.mp4` 拉到本地 0.25x 播。

3. **分层排除**：按时间线从外到内 / 从早到晚验证

   ```
   launcher icon transition
       → SystemUI task transition（QuickstepLaunchApp 等）
           → SplashScreen API splash window
               → Activity transition（overridePendingTransition）
                   → Compose Navigation transition
                       → app 内组件动画
   ```

   每一层用一个**最暴力的"全关"**改动验证是不是它（如 Compose Nav 改成 `EnterTransition.None` 无条件）。如果暴力关掉还有动画，说明根因更外层；如果消失，说明根因在这一层。

4. **找一个同类 app 对照**。商店里找一个功能相似、跨 ROM 都装得起来的 app（LibChecker 这种轻量小工具最适合）；如果它没问题，对比 manifest / theme / launchMode / 启动 API 差异，差异点就是 hypothesis。

5. **不要被 OEM perf hint 日志带偏**。Flyme PerfCore、HarmonyOS HiTrace 等 OEM 私有日志大量是性能优化 hint，与功能行为关系经常被过度解读。需要因果证据。

## 代码锚点

- `app/src/main/AndroidManifest.xml:23-26` — `MainActivity` 用 `launchMode="singleTask"`，这是诊断过程中唯一保留下来的改动，理由独立（落实热启动 `onNewIntent` 假设），与本对角动画问题无关
- `app/src/main/java/com/lightxin/MainActivity.kt:40-46` — `installSplashScreen()` 与 `setKeepOnScreenCondition`，方向 A 拆 SplashScreen API 时需要重写这段
- `app/src/main/res/values/themes.xml:10-14` — `Theme.LightXin.Splash` 继承 `Theme.SplashScreen`，方向 A 的修改入口
- `app/src/main/res/xml/shortcuts.xml` — 静态 shortcut 定义；曾试图加 `android:flags` 失败
- `app/src/main/java/com/lightxin/navigation/NavGraph.kt:115-129` — NavHost 默认 transition（`fadeIn + slideInHorizontally { it / 4 }`），曾被错误怀疑为根因
- `codestable/features/2026-05-12-app-shortcut-checkin/` — 本 feature 的 design / checklist / acceptance，本 learning 是该 feature acceptance 阶段沉淀

## 关联日志样本

下面是 Flyme 上诊断时抓到的关键 logcat 片段（已脱敏），保留给未来调试参考：

```
05-13 03:05:55.950 V/WindowManager: Creating SplashScreenStartingData
05-13 03:05:55.951 V/ShellStartingWindow: addSplashScreen for package: com.lightxin with theme: 7f0f0116
05-13 03:05:55.973 V/WindowManagerShell: onTransitionReady (#10492) ... QuickstepLaunchApp
05-13 03:05:58.259 V/WindowManager: Schedule remove starting ... animate=true
05-13 03:05:58.272 W/PerfCore: perf hint acquire release scene = splashscreen_exit_anim not exist
05-13 03:07:02.223 V/WindowManager: Creating SplashScreenStartingData
05-13 03:07:02.229 V/ShellStartingWindow: addSplashScreen for package: com.lightxin with theme: 7f0f0116
05-13 03:07:02.238 W/ll.splashscreen: type=1400 audit ... avc: denied { read } for name="u:object_r:vendor_display_prop:s0"
05-13 03:07:02.366 I/ActivityTaskManager: Displayed com.lightxin/.MainActivity for user 0: +138ms
```

- `QuickstepLaunchApp` 出现在 shortcut 启动这一帧 → SystemUI 处理动画的实锤
- `splashscreen_exit_anim not exist` 出现位置在 splash 显示**之后**、退出**之前** → 不是入口阶段的事件
- `avc: denied vendor_display_prop` 是 splash 子进程读显示驱动属性被 SELinux 拒，与 app 行为无关
- `Displayed ... +138ms` 说明 app 显示本身很快，对角动画的 ~300-500ms 视觉时长不在 app 渲染时间内 → 又一条"动画在 SystemUI 不在 app"的证据
