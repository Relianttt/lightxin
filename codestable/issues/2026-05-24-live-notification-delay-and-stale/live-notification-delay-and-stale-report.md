---
doc_type: issue-report
slug: live-notification-delay-and-stale
status: partially-fixed
reported: 2026-05-24
severity: medium
affects: [running, core/notification]
tags: [notification, flyme, android16, live-activity]
---

# 实况通知延迟显示 + Flyme 后台刷新停滞

## 现象

### 问题 1：通知显示延迟（Flyme + 原生共有）

- 用户点击"开始跑步"后，页面已进入跑步活动页、数据已开始刷新（Service 已启动、GPS 已回调）
- 但状态栏通知 / 实况胶囊约 10 秒后才出现
- Flyme 和 Android 原生设备均复现

### 问题 2：Flyme 后台刷新停滞

- 跑步进行中，app 进入后台约 10 秒后，实况通知卡片内容不再更新（距离/时长/速度冻结）
- app 内 RunningTracker 数据正常记录，回到前台后页面数据正确
- 仅 Flyme 设备观察到

## 复现步骤

1. 安装 debug APK，授予定位 + 通知权限
2. 首页 → 跑步 → 开始跑步
3. 观察：页面已显示跑步数据，但通知延迟约 10 秒才出现（问题 1）
4. 按 Home 键回到桌面，观察实况通知卡片内容约 10 秒后停止更新（问题 2）

## 环境

- 设备：MEIZU 20 / Flyme 12.6 / Android SDK 36
- 原生设备：具体型号待补充
- 版本：live-activity-notifier feature 首次实现后

## 已排除

- 不是网络请求延迟（`startRunning.do`）——页面数据已在刷新说明 Service 已启动
- 不是 channel importance 问题——改为 IMPORTANCE_LOW 后问题依旧
- 不是重复 notify 节流——去掉 `startForeground` 后的额外 `show()` 调用后问题依旧

## 当前状态（2026-05-24）

### 已修复：通知显示延迟

- Android promoted / Normal backend 已设置 `FOREGROUND_SERVICE_IMMEDIATE`，减少前台服务通知延迟展示。
- Flyme backend 也保留 `Notification.FOREGROUND_SERVICE_IMMEDIATE`，但不引入额外 Flyme 刷新实验代码。
- 课程倒计时通知已在首页课程数据落地后调用 `CourseNotificationScheduler.checkNow()`，避免只等下一轮 60 秒轮询。

### 未解决：Flyme 后台刷新停滞

- 现象仍存在：进入后台一段时间后，Flyme 实况通知中的距离/速度/文本停止刷新；系统 Chronometer 类时间展示可继续走，但静态 RemoteViews 字段不会自动变化。
- 根因分析：Flyme 后台进程冻结（`isFrozen=4`）阻止 app 重新 `notify()` 更新静态 RemoteViews 字段；Chronometer 由 SystemUI 自驱，不依赖 app 进程。通知限频（`numRateViolations=259`）使偶尔穿透的更新也被拦截。
- 星课表对照：星课表的通知模型是事件驱动的一次性通知（上课/下课 2-3 次更新），通过 `setExactAndAllowWhileIdle` 精确闹钟在固定时间点触发，不需要后台持续刷新。与 LightXin 跑步通知"每秒级连续更新"不是同一问题。
- 分层刷新策略：后台实况通知中时长（Chronometer）持续走；距离/速度在 app 可调度时更新；跑步记录本身以 RunningTracker 轨迹点为准。

### 已修复：深色模式字体反色

- RemoteViews 布局中 `android:textColor` 从硬编码 `#FF263238` 改为 `@color/live_notification_*` 资源引用，配合 `res/color-night/` 目录实现深色模式自动反色。
- `notification.live.contentColor` 从硬编码改为 `FlymeLiveBackend.isDarkMode()` 运行时检测：浅色模式 `#FF263238`，深色模式 `#FFFFFFFF`。

## 已尝试但未成功的 Flyme 刷新方案

- 把跑步通知刷新从定位回调中抽为 `RunningNotificationRefresher`，由服务内 coroutine 定时刷新：后台冻结后协程无法保证继续调度。
- 增加 `AlarmManager.setAndAllowWhileIdle` + manifest `BroadcastReceiver`，每 10 秒强制刷新并自续：抓日志确认只触发约 2 次，之后不再投递。
- 增加 `WAKE_LOCK`：对通知 RemoteViews 刷新链路无明显帮助，且不应作为长期方案。
- 调整 Flyme 展开卡/胶囊 RemoteViews、Chronometer、颜色和高优先级 channel：可以影响展示形态，但不能解决静态字段停刷；相关实验代码已回滚。
- 增加 Flyme marker service / `live_notification_config`：未证明能改善刷新停滞，已回滚。

## 下一步计划

- 当前接受分层刷新策略，不再引入 AlarmManager / WakeLock / 协程定时等已证实无效的保活方案。
- 不采用 `SCHEDULE_EXACT_ALARM` 精确闹钟：权限成本高、不适合连续刷新场景、且不保证穿透 Flyme freezer。
- 星课表真机对照（已完成代码分析，见 `tmp/star-schedule-analysis.md`）：星课表通知模型为事件驱动，不适用跑步连续刷新场景。
- 如其他厂商设备也出现类似后台停滞，优先评估引导用户开启电池优化白名单（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`），但预期要低——系统进程冻结和电池优化是两层机制。
- 低优先级实验（不承诺有效）：Flyme marker service 复测、`IMPORTANCE_HIGH` channel + 换 channel id。
- 再次复现时抓窄日志即可，不做全量 logcat。
