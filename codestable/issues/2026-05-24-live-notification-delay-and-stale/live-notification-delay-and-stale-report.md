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
- adb 采样证据显示，后台后 `com.lightxin` 仍是前台服务进程，`RunTrackingService` 仍存在；同时进程状态进入 `FGS` 且 `isFrozen=4`。
- 通知记录在冻结后卡住，例如 `android.text=距离 0.68 km · 时长 03:05`，对应 `mUpdateTimeMs=1779622670917` 不再变化。
- 实验期间 `dumpsys alarm` 里 `com.lightxin.running.NOTIFICATION_REFRESH` 只有 2 次 wakeup，后续未继续增加，说明普通 `setAndAllowWhileIdle` 自续 Broadcast 方案无法稳定穿透该后台状态。
- `dumpsys notification` 统计中出现过 `com.lightxin numRateViolations=259`，说明过高频率 notify 还可能触发系统通知限频，不能简单用更密集刷新解决。

## 已尝试但未成功的 Flyme 刷新方案

- 把跑步通知刷新从定位回调中抽为 `RunningNotificationRefresher`，由服务内 coroutine 定时刷新：后台冻结后协程无法保证继续调度。
- 增加 `AlarmManager.setAndAllowWhileIdle` + manifest `BroadcastReceiver`，每 10 秒强制刷新并自续：抓日志确认只触发约 2 次，之后不再投递。
- 增加 `WAKE_LOCK`：对通知 RemoteViews 刷新链路无明显帮助，且不应作为长期方案。
- 调整 Flyme 展开卡/胶囊 RemoteViews、Chronometer、颜色和高优先级 channel：可以影响展示形态，但不能解决静态字段停刷；相关实验代码已回滚。
- 增加 Flyme marker service / `live_notification_config`：未证明能改善刷新停滞，已回滚。

## 下一步计划

- 暂停继续修 Flyme 后台刷新，避免在没有新平台证据时继续堆保活逻辑。
- 下次继续时优先拉取并对比星课表或其他 Flyme 实况通知实现，重点看它们是否使用精确闹钟、前台服务自启动 action、厂商白名单/权限引导，或 Flyme 私有服务绑定回调。
- 再次复现时抓窄日志：`dumpsys notification --noredact`、`dumpsys alarm`、`dumpsys activity processes com.lightxin`、`notifyAsUser` / `AlarmManager` / Flyme SystemUI 关键词即可，避免完整 logcat 噪声。
- 如果确认厂商允许，下一轮再评估 `SCHEDULE_EXACT_ALARM`、前台服务自唤醒 action、或用户侧电池白名单提示；默认不引入高频 notify。
