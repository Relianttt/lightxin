---
doc_type: learning
track: pitfall
date: 2026-05-13
updated: 2026-07-19
slug: flyme-shortcut-launch-transition
component: [shortcut, splash, navigation, rom]
severity: low
tags: [shortcut, splashscreen-api, quickstep-launch-app, system-ui, rom-quirk, task-transition, remote-transition, starting-window, static-shortcut, dynamic-shortcut, on-new-intent]
---

# 已有 task 时点 App Shortcut 会触发 Quickstep 对角展开

## 问题

`app-shortcut-checkin` feature 实现完成后，在 app 已有后台 task 时，从桌面长按图标 → 点击 shortcut 进入 app，会出现一段明显的**左上至右下对角展开**动画，约 300-500ms，视觉嘈杂。从普通桌面图标热启动则没有 splash 和这段动画；force stop / 划掉 task 后再点 shortcut 属于冷启动，只出现更自然的从上而下过渡。

2026-05-13 的第一次分析将问题限定为 static shortcut 的 task 语义。2026-07-19 在已经改为 **dynamic shortcut**、Intent 不含 `CLEAR_TASK`、应用进程和 Activity 实例均存活的条件下再次受控复现，证实对角展开仍然存在。最终根因应修正为：**Launcher 把 App Shortcut 作为一次 Quickstep app launch 处理，显式提供 remote transition；WindowManager 即使复用既有 Activity，也会为这次 task switch 创建 Splash starting window，并把该 starting surface 纳入约 542ms 的 Quickstep 启动动画。**

SplashScreen API、Compose Navigation 和 Activity 是否重建都不是触发该动画的必要条件。static shortcut 的 `CLEAR_TASK` 是历史实现中的附加行为，可能导致 Activity 重建，但不是当前问题持续存在的根因。

## 2026-07-19 实机复核结论

### 受控条件

- 设备：MEIZU 20 / Flyme
- 应用版本：`1.2.0`，`versionCode=4`
- Manifest 已移除 `android.app.shortcuts` metadata；shortcut 由 `ShortcutManagerCompat.setDynamicShortcuts()` 注册
- `dumpsys shortcut` 显示两个入口均为 `flags=0x85 [DynIc-rStr]`
- 保存的 Intent 为 `flg=0x10000`（`FLAG_ACTIVITY_NO_ANIMATION`），**没有 `FLAG_ACTIVITY_CLEAR_TASK`**
- 点击前 LightXin 已退到后台，进程 PID `16942` 仍存活
- 点击前 taskId=`15775`，ActivityRecord=`42868825`

### 决定性证据

点击 dynamic shortcut 后：

| 证据 | 实际结果 | 说明 |
|---|---|---|
| 运行时 Intent | `act=DORM_CHECKIN flg=0x10010000` | 只有系统补入的 `NEW_TASK` + 应用设置的 `NO_ANIMATION`，没有 `CLEAR_TASK` |
| Activity 生命周期 | `wm_new_intent` → `onRestart` → `onStart` → `onResume` | 既有 `singleTask MainActivity` 被复用 |
| 实例身份 | PID=`16942`、taskId=`15775`、ActivityRecord=`42868825` 前后不变 | 没有杀进程、清 task 或重建 Activity |
| WindowManager 判定 | `startActivity=false newTask=false taskSwitch=true processRunning=true activityCreated=true` | 系统明确知道这是既有 Activity 的 task switch |
| Starting window | `SplashScreenStartingData` / `addSplashScreen ... task: 15775` | Activity 未重建，系统仍主动创建 Splash starting window |
| Task change | `m=TO_FRONT f=NO_ANIMATION|MOVE_TO_TOP` | task 实际只是提到前台，`NO_ANIMATION` 已被识别 |
| 动画执行者 | `RemoteTransition ... debugName=QuickstepLaunch` | Launcher 提供的远程动画仍接管转场 |
| 动画时长 | `finished=541.883ms` | 与肉眼观察的 300-500ms 对角展开一致 |

期间没有 `am_proc_start`、Activity finish/destroy/create；这直接排除了“当前 dynamic shortcut 仍因 `CLEAR_TASK` 重建 Activity”的解释。

### 修正后的根因链

```text
点击桌面长按菜单中的 dynamic shortcut
   ↓
Launcher3 将它作为 deep shortcut / app launch 处理
   ↓
Launcher 生成并传入 Quickstep RemoteTransition + shortcut 点击区域 sourceBounds
   ↓
LauncherAppsService 取出应用 Intent，并补入 FLAG_ACTIVITY_NEW_TASK
   ↓
singleTask MainActivity 被复用，收到 onNewIntent；既有 task 只是 TO_FRONT
   ↓
WindowManager 对这次 launcher task switch 仍创建 Splash starting window
   ↓
QuickstepLaunch 把 task / starting surface 从 shortcut 菜单项附近展开到全屏
   ↓
出现左上至右下的对角展开；约 542ms 后动画结束
```

### static `CLEAR_TASK` 假设的边界

AOSP `ShortcutParser.java` 确实会在解析 manifest static shortcut 时给第一个 Intent 加上：

```java
Intent.FLAG_ACTIVITY_NEW_TASK |
Intent.FLAG_ACTIVITY_CLEAR_TASK |
Intent.FLAG_ACTIVITY_TASK_ON_HOME
```

因此旧 static 版本可能额外发生 task 清空和 Activity 重建，这一源码事实本身没有错。但它不是对角展开的必要条件：当前 dynamic 版本已经完全绕过 `ShortcutParser` 的这些 flags，实机仍然以相同观感复现。真正跨 static / dynamic 都存在的是 `LauncherApps.startShortcut()` 的 Quickstep remote transition 和 task-switch starting window 路径。

## 症状

不同启动路径的表现：

| 入口 | 动画表现 | 解读 |
|---|---|---|
| force stop 后点 shortcut | 从上而下褪去，较自然 | 无既有 task，属于冷启动 |
| Recents 划掉 task 后点 shortcut | 同冷启动 | task 已移除 |
| app 在后台且进程存活时点 dynamic shortcut | 左上至右下对角展开 | 既有 Activity 收到 `onNewIntent`，但 Launcher 仍请求 Quickstep 启动转场，系统仍创建 Splash starting window |
| app 在前台，回桌面立刻点 shortcut | 同后台 shortcut | task 只做 `TO_FRONT`，不需要 Activity 重建也会复现 |
| 普通桌面图标后台点击 | 无 splash，无对角展开 | 普通热启动只是把 task 带回前台 |
| Recents 任务列表切回 | 无 | 同上 |

跨 ROM 对比：

- **Flyme**：shortcut 启动时由左向右平移
- **联想平板**（另一台 ROM）：**没有 splash**但有相同的对角动画——证明动画与 splash 显示与否解耦
- **多台国产 ROM**：均能观察到类似遮罩/平移动画
- **Pixel**：已有 task 时点 shortcut 同样有对角展开；证明这不是国产 ROM 专属行为，而是 Android / Quickstep 路径上的通用行为，OEM 只可能影响视觉强弱

辅助证据（关键 logcat）：

```
V/WindowManagerShell: onTransitionReady (#10492) ... QuickstepLaunchApp
W/PerfCore: perf hint acquire release scene = splashscreen_exit_anim not exist
```

`QuickstepLaunchApp` / `QuickstepLaunch` 是 Android 12+ SystemUI / WindowManagerShell 使用的 launcher → app **启动 remote transition**，基于 Launcher3 / Quickstep 框架。2026-07-19 的受控日志证明：即使 dynamic shortcut 只把既有 task 提到前台，Launcher 仍将这次操作标成 `type=OPEN` 并显式提交 Quickstep remote transition。

`splashscreen_exit_anim not exist` 是 Flyme PerfCore 的私有性能 hint 查询，**和这个动画的因果关系没有证据支持**——曾被一度误读为根因，详见下面"没用的做法"。

## 没用的做法

诊断顺序按时间线写。每条都附"当时的假设"和"为什么不成立"。

### 1. `setOnExitAnimationListener { provider.remove() }`

**假设**：以为是 Android 12+ SplashScreen API 的退出动画，listener 内立即 remove view 跳过默认动画。

**结果**：冷启动普通图标的 splash 退出动画确实被压制（侧面证明 listener 起作用），但 **已有 task 时点 shortcut 的对角展开还在**。

**为什么不成立**：splash exit 阶段发生在 SystemUI task transition 之**后**。已有 task 时点 static shortcut 触发的是 launcher / SystemUI 层 task transition，不是 app 内 splash view 的退出动画，listener 注册时机和作用域都不对。

### 2. shortcut XML 加 `android:flags="0x10000"`（`FLAG_ACTIVITY_NO_ANIMATION`）

**假设**：shortcut intent 默认带 `NEW_TASK + CLEAR_TASK`，触发 task transition；给 intent 加 `NO_ANIMATION` 让系统跳过转场。

**结果**：**编译失败**。

```
xml/shortcuts.xml:13: error: attribute android:flags not found.
```

**为什么不成立**：静态 shortcut 的 `<intent>` 标签是受限 schema，只支持 `action / targetPackage / targetClass` 和 `<extra>` 子标签。`android:flags` 在该 schema 里**不存在**。这是与 `<intent-filter>` / `<intent>`（在 manifest 外的）不同的解析器。

**2026-07-19 补充**：后来已通过 dynamic shortcut 成功写入 `FLAG_ACTIVITY_NO_ANIMATION`。实机 transition info 明确出现 `f=NO_ANIMATION|MOVE_TO_TOP`，但同一帧仍被委托给 `RemoteTransition ... QuickstepLaunch`。这证明 `NO_ANIMATION` 只能影响默认窗口动画，不能覆盖 Launcher 显式提供的 remote transition。

### 3. `launchMode="singleTask"`

**假设**：standard launchMode 下 shortcut intent 重建 Activity，让 SystemUI 走完整 launcher → app 动画。改 singleTask 后 Activity 实例唯一，shortcut 热启动走 `onNewIntent` 不重建，可能避开 transition。

**结果**：对角动画**仍在**。

**为什么不成立**：2026-07-19 日志已确认 `singleTask` 生效：同一 PID、taskId 和 ActivityRecord 收到 `wm_new_intent`，没有销毁重建。但 WindowManager 仍创建 Splash starting window，Quickstep remote transition 仍执行 541.883ms。Activity 是否复用与 Launcher 是否请求启动动画是两个独立维度。

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

### 7. 拆 SplashScreen API，改回传统 `windowBackground`

**假设**：LibChecker 没有调用 `installSplashScreen()`，所以它的 shortcut 虽然有 splash，但没有明显退出遮罩。由此推测 lightxin 只要把 `Theme.SplashScreen + installSplashScreen()` 拆掉，改成传统 `android:windowBackground` layer-list，就能复刻 LibChecker 的观感。

**结果**：实测无效。改动包括：

1. `Theme.LightXin.Splash` 不再继承 `Theme.SplashScreen`，改成普通主题并设置 `android:windowBackground`
2. 新建 `splash_window_background.xml`，用羊皮纸底色 + 居中 logo 复刻 splash 视觉
3. 删除 `MainActivity.installSplashScreen()`、`setKeepOnScreenCondition` 和 splash 放行协程

已有 task 时点 shortcut 的对角展开仍然存在，改动已回滚；新建的未跟踪 `splash_window_background.xml` 也已删除。

**为什么不成立**：交叉验证 LibChecker 后发现它并不是“完全没有 Android 12 splash”。它没有 `installSplashScreen()`，但 `values-v31/themes.xml` 配置了 `android:windowSplashScreenBackground`、`android:windowSplashScreenAnimatedIcon`、`android:windowSplashScreenAnimationDuration`。所以“LibChecker 没遮罩 = 不用 SplashScreen API”这个因果链不成立。更关键的是，lightxin 拆掉 API 后仍复现，直接排除 SplashScreen API 作为决定性根因。

## 解法

**截至 2026-07-19，仍没有已验证的、可稳定关闭该动画的应用层解法。**

dynamic shortcut 已成功避免 static shortcut 的 `CLEAR_TASK`，并让热启动真实走 `onNewIntent`，但没有消除对角展开。`FLAG_ACTIVITY_NO_ANIMATION` 也已被实机证明无效：WindowManager 的 task change 已带 `NO_ANIMATION`，Launcher 提供的 Quickstep remote transition 仍然直接接管动画。

保留 `MainActivity` 的 `launchMode="singleTask"`，因为它能避免不必要的 Activity 重建并正确分发 shortcut target；但不要再把它当成动画修复。具体见“代码锚点”。

## 为什么应用层无解

`QuickstepLaunchApp` / `QuickstepLaunch` 是 Android 12+ 的 Launcher3 / SystemUI 启动动画处理器。当前问题发生在“已有 task 时点 App Shortcut”路径，static 与 dynamic 均可进入；冷启动 shortcut 的从上而下过渡是另一种正常启动表现：

```
app 已有后台 task，Activity 与进程均存活
   ↓
点 App Shortcut
   ↓
Launcher 生成 Quickstep RemoteTransition 与 sourceBounds
   ↓
系统复用 MainActivity → onNewIntent；task change 为 TO_FRONT
   ↓
WindowManager 仍为 launcher task switch 创建 Splash starting window
   ↓
SystemUI WindowManagerShell 执行 QuickstepLaunch
   ↓ ←── 对角展开在这一层播放
starting window 移除 → 显示已有 Activity 内容
```

- 这个动画由 **Launcher / SystemUI remote transition** 控制，不在 app 进程
- 对已有 task 场景，它发生在 app 新内容可控渲染之外；不是 Compose 首帧之后的内部动画
- Pixel 也复现，说明不是国产 ROM 专属；OEM ROM 只影响动画观感强弱
- App Shortcut 通过 `LauncherApps.startShortcut()` 启动，Launcher 会附带点击区域和 Quickstep remote transition；普通图标热启动的 task 恢复策略与 starting-window 选择不同
- 普通 `ACTION_MAIN + CATEGORY_LAUNCHER` 路径走另一条 transition，OEM 没特殊处理，所以无遮罩
- `NO_ANIMATION` 只禁止默认 Activity/window animation，不能取消 Launcher 显式提交的 remote transition
- 应用层（包括 theme、launchMode、SplashScreen API、Activity transition、Compose Nav）无法直接控制 Launcher 提交的 `ActivityOptions`；已实测排除 Compose Nav、拆 SplashScreen API、dynamic shortcut 和 `NO_ANIMATION`

## LibChecker 对照

LibChecker（同类 Android App，在 shortcut 路径上观感更干净）曾被用作对照，但第一次解读有误。基于 `https://github.com/LibChecker/LibChecker` master 分支 `c316c9208905f0b71ee2038c6eb0bd3c84d7b7fd` 重新核对，并在同一台 MEIZU 20 上对已安装的 LibChecker `2.5.4` 做了实机 A/B 后，关键事实如下：

| 维度 | LibChecker | lightxin |
|---|---|---|
| `installSplashScreen()` | 没有调用 | 有调用 |
| Android 12 splash 资源 | `values-v31/themes.xml` 配了 `android:windowSplashScreen*` | `Theme.LightXin.Splash` 继承 `Theme.SplashScreen`，并配 `windowSplashScreen*` |
| launchMode | `singleTop` | 现 `singleTask` |
| shortcut intent | 静态 XML，custom action | 2026-05-13 原为静态 XML；当前为 dynamic shortcut + custom action |
| shortcut 目标 | 一个 `ChartActivity`、两个 `MainActivity` | 都 `MainActivity` |
| shortcut 后的应用内切换 | `ChartActivity` 直接启动；`MainActivity` 内 `ViewPager2.setCurrentItem(index, false)` 明确无动画 | `NavHost` 会按目标连续 navigate，但 Compose Nav 全关后仍复现，说明不是决定性根因 |

LibChecker 代码锚点：

- `app/src/main/AndroidManifest.xml:35-55` — app 级 `AppTheme`，`MainActivity` 为 `singleTop`，声明 `android.app.shortcuts`
- `app/src/main/res/xml/shortcuts.xml:6-37` — 三个静态 shortcut
- `app/src/main/res/values-v31/themes.xml:4-8` — 配置 Android 12 `android:windowSplashScreen*`
- `features/home/ui/MainActivity.kt:333-339` — shortcut action 只切换 ViewPager 页，且 `setCurrentItem(index, false)` 不播切换动画

**修正结论**：LibChecker 对照只能说明“它没有 `installSplashScreen()` 且 shortcut 后应用内落点切换更克制”，不能证明 SplashScreen API 是根因。lightxin 已经实测拆 SplashScreen API 无效；Compose Nav 全关也无效。因此 LibChecker 不是直接可复刻的应用层解法，只能作为“不要把 splash 和应用内导航混为一谈”的对照样本。

### LibChecker 同机实测

在 LibChecker 已有后台 task、进程仍存活时，从 Flyme 桌面长按菜单点击静态 shortcut“统计”，得到：

```text
dumpsys shortcut:
  flags=0x1a4 [ImManIc-rStr]
  Intent flg=0x1000c000

events:
  wm_destroy_activity ... finish-imm:clear-task-all
  wm_create_activity    ... START_STATISTICS
  wm_on_destroy_called  old ActivityRecord=121790809
  wm_on_create_called   new ActivityRecord=196598140

WindowManager:
  startActivity=true newTask=true taskSwitch=true
  processRunning=true activityCreated=false
  Creating SplashScreenStartingData

WindowManagerShell:
  type=OPEN
  RemoteTransition ... debugName=QuickstepLaunch
```

这组日志说明 LibChecker 的静态 shortcut 在当前 Android / Flyme 上确实被 AOSP `ShortcutParser` 加入 `NEW_TASK | CLEAR_TASK | TASK_ON_HOME`，旧 `MainActivity` 被清掉重建；它同样创建 Splash starting window，并同样走 Quickstep remote transition。LibChecker 没有“绕过 Quickstep”的隐藏写法。它观感较干净，更可能来自以下视觉和应用内实现差异：

- shortcut action 落点只调用 `ViewPager2.setCurrentItem(index, false)`，没有第二段应用内导航动画；
- starting theme 直接使用应用背景与专用 splash 动画，画面衔接更统一；
- application 同时声明 `icon` 与 `roundIcon`，adaptive icon 资源链更完整；LightXin 当前只声明 `icon`；
- Flyme 对不同图标和 starting surface 的 morph / 裁剪退化表现可能不同。

这些差异最多解释“为什么看起来没那么突兀”，不能解释“为什么没有系统转场”；实机日志已经证明系统转场仍然存在。

## 未来修复方向（如果回来再尝试）

已排除的方向不要再重复：

- **拆 SplashScreen API**：已实测无效。
- **关闭 Compose Nav transition**：已实测无效，包括 shortcut 期间关闭和全局无条件关闭。
- **`setOnExitAnimationListener { provider.remove() }`**：只影响 splash exit view，不影响 SystemUI task transition。
- **`launchMode` / Activity transition override**：已实测无效。

### 已验证无效：动态 shortcut + `FLAG_ACTIVITY_NO_ANIMATION`

当前代码已移除 manifest static metadata，并通过 `ShortcutManagerCompat.setDynamicShortcuts()` 注册。实机 `dumpsys shortcut` 证明 Launcher 使用的确实是 dynamic shortcut，不存在旧 static 缓存；Activity 也真实走 `onNewIntent`。

该改动解决了“static shortcut 可能因 `CLEAR_TASK` 重建 Activity”的副问题，但没有改变 Quickstep remote transition。`FLAG_ACTIVITY_NO_ANIMATION` 也已确认不能取消 Launcher 提交的 remote animation。

### 方向 A：控制 shortcut starting window 的视觉

Android 12 / API 31 起，平台官方提供 `ShortcutInfo.Builder.setStartingTheme(themeResId)`；静态 shortcut 也有对应的 `android:splashScreenTheme` 属性。AOSP `LauncherAppsService.startShortcutInner()` 会读取该主题并通过 `KEY_SPLASH_SCREEN_THEME` 传给 WindowManager。因此最值得做的下一轮实验不是再改全局 Splash，而是只给 dynamic shortcut 指定专用 starting theme：

1. 首先让 shortcut starting theme 使用与目标首帧一致的纯色背景和更克制的图标，验证能否消除明显的 Splash 遮罩感；
2. 再单独测试该主题的 `android:windowDisablePreview=true`，观察日志是否从 `Disable=false` 变为 `Disable=true`、是否不再创建 `SplashScreenStartingData`；
3. 同时补齐 `android:roundIcon="@mipmap/ic_launcher_round"`，复测 Flyme 是否仍出现 `Not a adaptive icon`，排除图标 morph 退化。

**边界**：`setStartingTheme()` 是官方可控入口，但只负责 shortcut 的 starting surface。即使 `windowDisablePreview` 成功去掉 Splash starting window，Launcher 提交的 `QuickstepLaunch` remote transition 仍可能继续播放；联想平板“无 splash 仍有对角展开”的历史观察也说明它最多可能把问题从“Splash + 对角展开”降级为“仅对角展开”。在 MEIZU 20 实测前不能把它写成确定修复。

### 已验证无效：专用 Splash 中转 Activity

2026-07-19 在 Pixel 4 上完成实机实验。实现方式：

1. dynamic shortcut 指向独立 `ShortcutSplashActivity`，Intent 使用 `NEW_DOCUMENT | MULTIPLE_TASK`；
2. 中转 Activity 使用 `taskAffinity=""`、`documentLaunchMode="always"`、`noHistory`、`excludeFromRecents`，让 Launcher 的 shortcut 启动只作用于一次性临时 task；
3. 中转 Activity 通过官方 `installSplashScreen()` 完整显示与主入口一致的 Splash；
4. Splash 完成后用 `NEW_TASK | CLEAR_TOP | SINGLE_TOP | NO_ANIMATION` 和零动画 `ActivityOptions` 把 action 交给 `MainActivity`，最后 `finishAndRemoveTask()`。

已有 task 的事件证据：

```text
wm_create_activity  ShortcutSplashActivity  task=108
wm_on_resume_called ShortcutSplashActivity
wm_task_to_front    task=106
wm_new_intent       MainActivity             ActivityRecord=126277397
wm_finish_activity  ShortcutSplashActivity
wm_task_removed     task=108
```

原 `MainActivity` 的 taskId 和 ActivityRecord 前后不变，说明临时 task 和无动画交接在任务栈层面完全按设计工作；扫码与查寝两个 action 也均能到达正确页面。

逐帧结果：点击后约 0-240ms 仍是 `QuickstepLaunch` 基于 shortcut 菜单项 `sourceBounds` 的圆角矩形展开；约 240-850ms 为完整全屏 Splash；约 900ms 无动画显示目标页。普通图标冷启动与 shortcut 方案在 400ms、800ms、1200ms 的 Splash 主体一致。冷启动虽然服务端会依次创建中转 Activity 和 `MainActivity`，但二者使用相同 Splash 资源，实测没有 Logo 重播、闪白或黑帧。

**最终验收**：用户实机观察后确认问题没有修复。虽然方案能把被展开的内容换成完整 Splash，但第一段异常展开仍然存在，并额外引入约 0.9 秒的刻意等待、临时 task 和第二次 Activity 交接。它只是在视觉上遮盖问题，没有改变根因路径，不满足修复标准，实验代码已全部回滚。

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

4. **找一个同类 app 对照，但不要只看表面差异**。LibChecker 这次第一次就被误读为“完全不用 SplashScreen API”，后来交叉验证才发现它仍配置了 Android 12 `android:windowSplashScreen*`。对照样本要同时查 manifest、theme、values-v31、shortcut 目标 Activity 和应用内落点动画。

5. **不要被 OEM perf hint 日志带偏**。Flyme PerfCore、HarmonyOS HiTrace 等 OEM 私有日志大量是性能优化 hint，与功能行为关系经常被过度解读。需要因果证据。

## 代码锚点

- `app/src/main/AndroidManifest.xml:28-37` — `MainActivity` 使用 `singleTask`；已移除 `android.app.shortcuts` metadata
- `app/src/main/java/com/lightxin/LightXinApp.kt:8-12` — Application 启动时注册 dynamic shortcut
- `app/src/main/java/com/lightxin/navigation/ShortcutRegistrar.kt:15-58` — dynamic shortcut Intent 带 `FLAG_ACTIVITY_NO_ANIMATION`；实机证明该 flag 不能取消 Quickstep remote transition
- `app/src/main/java/com/lightxin/MainActivity.kt:43-71` — `installSplashScreen()` 与 keep condition；不是本次热启动 starting window 的触发者
- `app/src/main/java/com/lightxin/MainActivity.kt:110-129` — dynamic shortcut 热启动真实进入 `onNewIntent`，2026-07-19 实机已验证
- `app/src/main/res/values/themes.xml:8-14` — Activity 的 Splash theme 决定 task-switch starting window 的视觉内容，但不是 remote transition 的所有者
- `app/src/main/res/xml/shortcuts.xml` — 历史 static shortcut 资源；当前 Manifest 未引用
- `app/src/main/java/com/lightxin/navigation/NavGraph.kt:94-143` — NavHost shortcut 编排与 Compose transition；全关后仍复现，不是决定性根因
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

### 2026-07-19 dynamic shortcut 受控样本

```text
dumpsys shortcut:
  flags=0x85 [DynIc-rStr]
  intents=[Intent { act=com.lightxin.action.DORM_CHECKIN flg=0x10000 cmp=com.lightxin/.MainActivity }]

ActivityTaskManager:
  START ... act=com.lightxin.action.DORM_CHECKIN flg=0x10010000
  showStartingWindow ... startActivity=false newTask=false taskSwitch=true
  processRunning=true activityCreated=true

events:
  wm_new_intent       ActivityRecord=42868825 task=15775
  wm_on_restart_called pid=16942
  wm_on_start_called   pid=16942
  wm_on_resume_called  pid=16942

WindowManagerShell:
  m=TO_FRONT f=NO_ANIMATION|MOVE_TO_TOP
  RemoteTransition ... debugName=QuickstepLaunch
  Finish Transition ... finished=541.883ms
```

这组日志同时证明四件事：当前入口是 dynamic、没有 `CLEAR_TASK`、Activity 没有重建、`NO_ANIMATION` 没有阻止 Quickstep remote transition。

## 2026-07-19 更新

- 用 AOSP `ShortcutParser.java` 复核 static shortcut 的强制 flags，但明确其只解释旧 static 实现的附加 task 行为。
- 在 MEIZU 20 上完成进程存活条件下的 dynamic shortcut 受控复现。
- 将最终根因从“static shortcut 清 task / Activity 重建”修正为“Launcher Quickstep remote transition + task-switch starting window”。
- 将 dynamic shortcut 与 `FLAG_ACTIVITY_NO_ANIMATION` 从未来方向改为已验证无效。
- 克隆并核对 LibChecker master `c316c9208905f0b71ee2038c6eb0bd3c84d7b7fd`，并在同一台 MEIZU 20 上对已安装的 LibChecker `2.5.4` 做静态 shortcut A/B；确认其同样清 task、重建 Activity、创建 Splash starting window并走 `QuickstepLaunch`。
- 找到 API 31+ 官方 `ShortcutInfo.Builder.setStartingTheme()` 与 AOSP 服务端传递链，将 shortcut 专用 starting theme / `windowDisablePreview` 列为下一轮唯一高价值应用层实验，但不预设能够取消 Quickstep remote transition。
