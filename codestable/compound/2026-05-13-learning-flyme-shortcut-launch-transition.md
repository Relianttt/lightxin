---
doc_type: learning
track: pitfall
date: 2026-05-13
updated: 2026-05-13
slug: flyme-shortcut-launch-transition
component: [shortcut, splash, navigation, rom]
severity: low
tags: [shortcut, splashscreen-api, quickstep-launch-app, system-ui, rom-quirk, task-transition, libchecker, static-shortcut]
---

# 已有 task 时点 static shortcut 会触发 SystemUI 对角展开

## 问题

`app-shortcut-checkin` feature 实现完成后，在 app 已有后台 task 时，从桌面长按图标 → 点击 shortcut 进入 app，会出现一段明显的**左上至右下对角展开**动画，约 300-500ms，视觉嘈杂。从普通桌面图标热启动则没有 splash 和这段动画；force stop / 划掉 task 后再点 shortcut 属于冷启动，只出现更自然的从上而下过渡。

本次为修这个动画花了大半天，绕过多条死胡同后定位到根因是 static shortcut 在已有 task 时触发的 SystemUI / WindowManagerShell task transition。后续又用 Pixel、LibChecker 和拆 SplashScreen API 做过交叉验证：这是 Android / Quickstep 路径上的行为，SplashScreen API 不是决定性根因，Compose Navigation 动画也已被排除。本文把全部弯路写下来，避免下次再陷进去。

## 症状

不同启动路径的表现：

| 入口 | 动画表现 | 解读 |
|---|---|---|
| force stop 后点 shortcut | 从上而下褪去，较自然 | 无既有 task，属于冷启动 |
| Recents 划掉 task 后点 shortcut | 同冷启动 | task 已移除 |
| app 在后台时点 shortcut | 左上至右下对角展开 | 既有 task 被 static shortcut 路径清理/重建 |
| app 在前台，回桌面立刻点 shortcut | 同后台 shortcut | 仍然存在既有 task |
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

`QuickstepLaunchApp` 是 Android 12+ SystemUI / WindowManagerShell 用的 launcher → app **启动 transition handler**，基于 Launcher3 / Quickstep 框架。当前证据指向：已有 task 时触发 static shortcut，会走不同于普通图标热启动的 Quickstep task transition，并产生对角展开动画。

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

### 3. `launchMode="singleTask"`

**假设**：standard launchMode 下 shortcut intent 重建 Activity，让 SystemUI 走完整 launcher → app 动画。改 singleTask 后 Activity 实例唯一，shortcut 热启动走 `onNewIntent` 不重建，可能避开 transition。

**结果**：对角动画**仍在**。

**为什么不成立**：SystemUI 的 task transition 选 handler 是基于 launcher shortcut 路径和 task 操作语义，而不是只看 Activity 实例是否复用。Activity 不重建只省了 `onCreate`，省不了 SystemUI 那一帧。

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

**没有已验证的应用层解法。接受为 SystemUI / launcher shortcut transition 行为。**

回滚所有诊断改动到无效的部分；保留 `MainActivity` 的 `launchMode="singleTask"`（独立于本问题的合理改动）。具体见"代码锚点"。

## 为什么应用层无解

`QuickstepLaunchApp` 是 Android 12+ 引入的 SystemUI 启动动画处理器。当前问题只发生在“已有 task 时点 static shortcut”这一路径，冷启动 shortcut 的从上而下过渡是另一种正常启动动画：

```
app 已有后台 task
   ↓
点 static shortcut
   ↓
launcher / system 按 static shortcut 的 task 语义处理既有 task
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
- 对已有 task 场景，它发生在 app 新内容可控渲染之前；不是 Compose 首帧之后的内部动画
- Pixel 也复现，说明不是国产 ROM 专属；OEM ROM 只影响动画观感强弱
- static shortcut 的 back stack / task 启动语义不同于普通图标热启动，所以普通图标只是 task reorder，shortcut 会出现对角展开
- 普通 `ACTION_MAIN + CATEGORY_LAUNCHER` 路径走另一条 transition，OEM 没特殊处理，所以无遮罩
- 应用层（包括 manifest、theme、launchMode、SplashScreen API、Activity transition、Compose Nav）全都在 SystemUI 之**后**或之**外**生效；已实测排除 Compose Nav 和拆 SplashScreen API

## LibChecker 对照

LibChecker（同类 Android App，在 shortcut 路径上观感更干净）曾被用作对照，但第一次解读有误。基于 `https://github.com/LibChecker/LibChecker` master 分支 `c54964b2681d8da6a83fff180606d5be08103d22` 重新核对后，关键事实如下：

| 维度 | LibChecker | lightxin |
|---|---|---|
| `installSplashScreen()` | 没有调用 | 有调用 |
| Android 12 splash 资源 | `values-v31/themes.xml` 配了 `android:windowSplashScreen*` | `Theme.LightXin.Splash` 继承 `Theme.SplashScreen`，并配 `windowSplashScreen*` |
| launchMode | `singleTop` | 现 `singleTask` |
| shortcut intent | 静态 XML，custom action | 静态 XML，custom action |
| shortcut 目标 | 一个 `ChartActivity`、两个 `MainActivity` | 都 `MainActivity` |
| shortcut 后的应用内切换 | `ChartActivity` 直接启动；`MainActivity` 内 `ViewPager2.setCurrentItem(index, false)` 明确无动画 | `NavHost` 会按目标连续 navigate，但 Compose Nav 全关后仍复现，说明不是决定性根因 |

LibChecker 代码锚点：

- `app/src/main/AndroidManifest.xml:35-55` — app 级 `AppTheme`，`MainActivity` 为 `singleTop`，声明 `android.app.shortcuts`
- `app/src/main/res/xml/shortcuts.xml:6-37` — 三个静态 shortcut
- `app/src/main/res/values-v31/themes.xml:4-8` — 配置 Android 12 `android:windowSplashScreen*`
- `features/home/ui/MainActivity.kt:333-339` — shortcut action 只切换 ViewPager 页，且 `setCurrentItem(index, false)` 不播切换动画

**修正结论**：LibChecker 对照只能说明“它没有 `installSplashScreen()` 且 shortcut 后应用内落点切换更克制”，不能证明 SplashScreen API 是根因。lightxin 已经实测拆 SplashScreen API 无效；Compose Nav 全关也无效。因此 LibChecker 不是直接可复刻的应用层解法，只能作为“不要把 splash 和应用内导航混为一谈”的对照样本。

## 未来修复方向（如果回来再尝试）

已排除的方向不要再重复：

- **拆 SplashScreen API**：已实测无效。
- **关闭 Compose Nav transition**：已实测无效，包括 shortcut 期间关闭和全局无条件关闭。
- **`setOnExitAnimationListener { provider.remove() }`**：只影响 splash exit view，不影响 SystemUI task transition。
- **`launchMode` / Activity transition override**：已实测无效。

当前试验方向：

### 方向 A：动态 shortcut + `FLAG_ACTIVITY_NO_ANIMATION`

`ShortcutManagerCompat.setDynamicShortcuts()` 注册同 ID 的动态 shortcut 覆盖静态版，构造 intent 时加 `FLAG_ACTIVITY_NO_ANIMATION`（`0x10000`）。

试验实现注意：为避免 launcher 继续展示 manifest static shortcut，测试版先移除 `android.app.shortcuts` metadata，只保留运行时注册的 dynamic shortcut。代价是首次启动 App 前长按图标不会出现 shortcut；如果验证有效，再决定是否接受这个产品代价，或改成静态 + 动态并存的兼容策略。

**风险**：增加 shortcut 注册时机（首次启动后），launcher 缓存与刷新需要小心。系统不一定尊重 framework flag（它的 SystemUI handler 可能直接无视）。

### 方向 B：跑 ActivityOptions 自定义 launch animation

在某个对外可触发 shortcut 的入口（如果有）用 `ActivityOptions.makeCustomAnimation()` 替代默认 transition。但 launcher 调起的 shortcut 不经过 app 代码，这条对 launcher 启动场景无效，只对 app 内自启场景有效。可能 0 收益。

### 方向 C：动手调 SystemUI（不可行）

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

- `app/src/main/AndroidManifest.xml:23-26` — `MainActivity` 用 `launchMode="singleTask"`，这是诊断过程中唯一保留下来的改动，理由独立（落实热启动 `onNewIntent` 假设），与本对角动画问题无关
- `app/src/main/java/com/lightxin/MainActivity.kt:40-46` — `installSplashScreen()` 与 `setKeepOnScreenCondition`；拆掉这段已实测无效，不要再当优先方案
- `app/src/main/res/values/themes.xml:10-14` — `Theme.LightXin.Splash` 继承 `Theme.SplashScreen`；改成传统 `windowBackground` 已实测无效
- `app/src/main/res/xml/shortcuts.xml` — 静态 shortcut 定义；曾试图加 `android:flags` 失败
- `app/src/main/java/com/lightxin/navigation/NavGraph.kt:115-129` — NavHost 默认 transition（`fadeIn + slideInHorizontally { it / 4 }`），曾被错误怀疑为根因；全关后仍复现
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
