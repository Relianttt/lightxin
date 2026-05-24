# 星课表实况通知实现分析

> 研究日期：2026-05-24
> 源码：`https://github.com/lightStarrr/starSchedule`（本地克隆 `C:/tmp/lightxin-live-notification-research/starSchedule/`）
> 目的：对比星课表，找出 LightXin 缺失的 Flyme 实况通知关键机制

## 1. 星课表通知模型 vs LightXin

**星课表是事件驱动的一次性通知，不是持续刷新：**

```
用户设置课程提醒 → AlarmManager 定时唤醒 → 发通知 → 课程开始时更新一次 → 5分钟后取消
```

没有循环刷新、没有持续更新距离/时长/速度的需求。这就是它和 LightXin 最根本的差异——星课表根本不需要解决"后台持续刷新通知"的问题，因为它的通知只在固定时间点变化一共 2-3 次。

**LightXin 是持续流式更新：**

```
开始跑步 → startForeground + 通知 → GPS 每秒回调 → 更新距离/时长/速度 → 持续 N 分钟
```

这是完全不同的模型。GPS 回调是高频的，通知需要跟着 GPS 持续刷新。

## 2. 星课表有而 LightXin 缺失的机制

### 2.1 Flyme 标记服务（marker service）——最关键差异

星课表的 AndroidManifest 中声明了一个特殊的 Service：

```xml
<service
    android:name=".service.LiveNotification"
    android:exported="true">
    <intent-filter>
        <action android:name="com.android.systemui.LIVE_NOTIFICATION_SERVICE" />
    </intent-filter>
    <meta-data
        android:name="live_notification_config"
        android:resource="@xml/live_notification_config" />
</service>
```

配合 `live_notification_config.xml`：

```xml
<liveNotification>
    <title>星课表</title>
    <description>课前提醒</description>
    <icon>@mipmap/ic_launcher</icon>
    <isService>false</isService>
</liveNotification>
```

而 `LiveNotification` Service 本身是空的——不干活，只是声明存在：

```kotlin
class LiveNotification : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
```

**这是 Flyme 系统识别"合法实况通知提供者"的注册机制**。它的作用类似 Android 的 `TileService` ——不是真的执行逻辑，而是向系统声明"我有资格发实况通知"。没有它，SystemUI 可能不把该 app 的通知当作"实况通知"来对待，从而在后台不给予特殊保活待遇。

**LightXin 当前：完全没有这个声明。**

### 2.2 IMPORTANCE_HIGH vs IMPORTANCE_LOW

| | 星课表 | LightXin |
|---|---|---|
| Channel importance | `IMPORTANCE_HIGH` | `IMPORTANCE_LOW` |
| Channel bypass DND | `setBypassDnd(true)` | 无 |

平台契约文档也建议 HIGH。LightXin 用了 LOW 可能是为了避免声音/震动干扰，但 HIGH 对后台通知优先级有实际影响。

### 2.3 精确闹钟权限 + 系统唤醒

星课表声明了 LightXin 没有的权限：

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

- `SCHEDULE_EXACT_ALARM`：允许 `AlarmManager.setExactAndAllowWhileIdle()` 精确穿透 Doze
- `RECEIVE_BOOT_COMPLETED`：重启后恢复闹钟（否则重启后通知全部丢失）
- `WAKE_LOCK`：声明了但星课表也未实际大量使用，主要是保底

星课表用 `setExactAndAllowWhileIdle()` 来安排 "课程开始时更新通知" 和 "课程开始后 5 分钟取消通知" 两个精确时间点。这个 API 是系统级别的，能穿透 Doze 和进程冻结——因为系统承诺在精确时间点给 app 短暂的 CPU 窗口。

### 2.4 星课表的通知更新链路

```
AlarmManager.setExactAndAllowWhileIdle(triggerTime)
    ↓ 系统承诺在该时间点唤醒进程
CourseNotificationUpdateReceiver.onReceive()
    ↓ 同步调用（在 BroadcastReceiver 的有限时间内）
UnifiedNotificationManager.showCourseNotificationImmediate(finish=true)
    ↓ 直接构建 Notification 并 notify()
notificationManager.notify(NOTIFICATION_ID, notification)
```

这条链路全程同步，没有协程跳转，不依赖进程持续存活。关键是把"通知更新"挂在 `setExactAndAllowWhileIdle` 这个系统级保障上，而不是依赖 app 自身的前台服务维持活性。

## 3. 对 LightXin 跑步通知的启示

### 3.1 星课表的方案不能直接套用

跑步通知需求"每秒更新距离/速度"和星课表"上课/下课两次更新"是根本不同的。星课表没有解决连续更新问题，它回避了这个问题。

### 3.2 可以从星课表借鉴的

| 机制 | 作用 | 对跑步的帮助 |
|---|---|---|
| Flyme marker service | 让系统识别为合法实况通知提供者 | **可能**给更多后台执行预算 |
| `IMPORTANCE_HIGH` channel | 提高通知优先级 | 可能减少系统对通知更新的节流 |
| `SCHEDULE_EXACT_ALARM` | 精确穿透 Doze 唤醒 | 已知 LightXin 的 `setAndAllowWhileIdle` 自续方案只触发了 2 次，可能需要 `setExact` 变体 |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复 | 跑步过程中重启场景不常见，但完整性考虑 |

### 3.3 marker service 最值得尝试

在三个差异中，Flyme marker service 是 LightXin 完全没有、且可能影响系统对进程管理策略的机制。即使不能解决跑步的连续更新问题，至少能让 Flyme 系统正确识别 LightXin 的实况通知身份，可能：
- 减少 SystemUI 对通知更新的丢弃
- 在前台服务转为后台时给予更长的执行窗口
- 避免通知被降级为普通通知

### 3.4 一个可能的混合方案

借鉴星课表的 "AlarmManager 精确唤醒" 思路：

```
// 在 RunTrackingService.notifyState() 中：
// 不用 callbackFlow 依赖 GPS 回调来触发 notify()
// 而是：
// 1. GPS 回调只更新 RunningTracker 内存状态
// 2. 用独立协程每 5 秒读取 tracker.state 并发 notify()
// 3. 当检测到 app 进入后台后，改用 AlarmManager.setExactAndAllowWhileIdle(每5秒)
//    自续模式来穿透冻结
```

但这个方案之前已经试过（`RunningNotificationRefresher` + `setAndAllowWhileIdle`），失败了（只触发了 2 次）。可能 `setExact` 变体会更可靠，或者需要配合 marker service 一起使用才有用。

## 4. 结论

星课表之所以"后台通知不卡"，**不是因为它解决了持续更新的问题，而是因为它根本没有持续更新需求**。它的通知模型是：提前设好精确闹钟 → 闹钟触发时系统给短暂 CPU 窗口 → 在这个窗口里同步发一条通知 → 结束。整个过程不需要进程持续存活。

LightXin 的跑步通知需求本质上是 **"需要在后台持续获得 CPU 时间来刷新通知"** ，这是完全不同的难题。Flyme marker service 是 LightXin 当前最明显的缺失项，加上它可能改善系统对通知的待遇，但对进程冻结问题本身不太可能根本解决——因为即便是前台服务，Flyme 也会在后台冻结它。
