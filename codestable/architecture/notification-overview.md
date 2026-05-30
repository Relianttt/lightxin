---
doc_type: architecture
slug: notification-overview
scope: core/notification/ 的实况通知抽象层，覆盖统一门面、三平台 backend、设备能力检测、课程倒计时调度、权限引导开关，以及与 RunTrackingService / HomeViewModel / MoreFeaturesScreen 的消费关系
summary: notification 以 LiveActivityNotifier 为统一门面，根据设备能力自动选择 Flyme / Android16 / Normal backend；更多功能页提供默认开启的实况通知权限引导开关，用于展示精确闹钟与电池优化白名单状态
status: current
last_reviewed: 2026-05-31
tags: [notification, flyme, android16, live-activity, running, schedule, permission]
depends_on: [running-overview, home-overview, schedule-overview]
---

# 实况通知模块总览

## 0. 术语

- **LiveActivityNotifier** — 业务层唯一调用入口，负责 backend 选择、通知 ID 映射、show/cancel 操作
- **LiveActivityRequest** — 业务层构造的通用请求，不含任何平台私有字段
- **Backend** — 平台特定的通知构建实现（Flyme / Android16 / Normal 三选一）
- **DeviceCapability** — 运行时检测设备支持的通知能力，决定走哪个 backend
- **CourseNotificationScheduler** — 课程倒计时通知的定时检查调度器
- **实况通知权限引导开关** — `MoreFeaturesScreen` 中默认开启的设置项；开启时在同一张卡片内展示精确闹钟权限与电池优化白名单状态，关闭时仅保留“实况通知”开关行

## 1. 定位与受众

本 doc 描述 `core/notification/` 的职责边界：

- 提供跨 feature 的实况通知抽象，业务层只依赖统一门面
- 隔离 Flyme 私有协议、Android 16 promoted ongoing、普通通知三条路径
- 当前消费者：跑步前台服务（RunTrackingService）、课程倒计时（CourseNotificationScheduler）

适用读者：

- 要接入新的实况通知消费者（如查寝签到）的人
- 要修改通知展示样式或平台适配的人
- 要排查通知不显示、延迟、刷新停滞的人

## 2. 结构与交互

### 2.1 整体架构

```
业务层（RunTrackingService / CourseNotificationScheduler）
    ↓ 构造 LiveActivityRequest
LiveActivityNotifier（门面，@Singleton）
    ↓ DeviceCapability.resolve()
┌─────────────────┬──────────────────────┬─────────────┐
│ FlymeLiveBackend │ AndroidPromotedBackend │ NormalBackend │
│ RemoteViews      │ BigTextStyle           │ BigTextStyle  │
│ is_live extras   │ promoted ongoing       │ 普通 ongoing  │
└─────────────────┴──────────────────────┴─────────────┘
    ↓
NotificationManager.notify(stableId, notification)
```

### 2.2 设备能力检测优先级

`DeviceCapability.resolve()` 按以下顺序判断：

1. **Flyme**：manufacturer=meizu + Flyme≥11 + `isNotificationLiveEnabled` 返回 true
2. **Android16**：SDK≥36 + `canPostPromotedNotifications()` 返回 true
3. **Normal**：兜底

锚点：`core/notification/DeviceCapability.kt:22-53`

### 2.3 通知 ID 稳定映射

`LiveActivityNotifier` 内部维护 `key → notificationId` 映射，保证同一业务 key 的 show/update 覆盖同一条通知，不产生重复项。

锚点：`core/notification/LiveActivityNotifier.kt:27-29`

### 2.4 通知点击路由

三个 backend 共用 PendingIntent 构建逻辑：Intent 携带 `notification_route` extra → `MainActivity.onNewIntent` 提取 → NavGraph LaunchedEffect 导航到目标页面。

锚点：`MainActivity.kt:onNewIntent` / `navigation/NavGraph.kt:LaunchedEffect(pendingNotificationRoute)`

### 2.5 更多功能页权限引导

`MoreFeaturesScreen` 持有 `live_notification_enabled` 偏好，默认值为 `true`。该开关当前只控制权限引导 UI 的展开/收起：关闭时卡片内只显示“实况通知”一项；开启时同卡片下方展开“精确闹钟权限”和“电池优化白名单”两项状态，并提供跳转系统设置的入口。

权限状态在页面 `ON_RESUME` 时刷新：

- Android 12+ 精确闹钟：`AlarmManager.canScheduleExactAlarms()`；点击跳 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
- Android 6+ 电池优化白名单：`PowerManager.isIgnoringBatteryOptimizations(packageName)`；未加入时跳 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

锚点：`feature/more/ui/MoreFeaturesScreen.kt:71-148,261-292` / `AndroidManifest.xml:14-15`。

## 3. 三个 Backend 的差异

| 维度 | FlymeLiveBackend | AndroidPromotedBackend | NormalBackend |
|---|---|---|---|
| Builder | `Notification.Builder` | `NotificationCompat.Builder` | `NotificationCompat.Builder` |
| Channel importance | LOW | LOW | LOW |
| 胶囊/Chip | `notification.live.capsuleContent` | `android.shortCriticalText` | 无 |
| 展开样式 | `setCustomContentView(RemoteViews)` | `BigTextStyle` | `BigTextStyle` |
| 私有 extras | `is_live` + `notification.live.*` | `android.requestPromotedOngoing` | 无 |
| RemoteViews | ✅ 必须 | ❌ 禁止 | ❌ 不使用 |
| 深色模式 | `notification.live.contentColor` 动态 + `res/color-night/` 资源 | 系统自动 | 系统自动 |

### 3.1 Flyme RemoteViews 布局分支

`FlymeLiveBackend.buildRemoteViews()` 根据 extras 中是否有 `courseName` 字段选择布局：

- 有 `courseName` → `notification_live_course.xml`（星课表风格：课程名+倒计时+地点）
- 无 → `notification_live_running.xml`（跑步：距离大字+时长速度GPS）

锚点：`core/notification/FlymeLiveBackend.kt:68-84`

### 3.2 Flyme 深色模式适配

Flyme 实况通知的展开卡片由系统渲染背景，RemoteViews 内的文字颜色需跟随系统深色模式：

- **布局层**：`notification_live_running.xml` 和 `notification_live_course.xml` 中的 `android:textColor` 不硬编码，改为引用 `@color/live_notification_*`。系统根据当前主题自动选择 `res/color/`（浅色）或 `res/color-night/`（深色）目录下的值。
- **extras 层**：`notification.live.contentColor` 不再硬编码 `#FF263238`，改为 `FlymeLiveBackend.isDarkMode(context)` 运行时检测：浅色模式用 `#FF263238`，深色模式用 `#FFFFFFFF`。

颜色资源文件：

| 资源 | 浅色 (`res/color/`) | 深色 (`res/color-night/`) |
|---|---|---|
| `live_notification_title` | `#FF263238` | `#FFFFFFFF` |
| `live_notification_secondary` | `#FF54656D` | `#FFB0BEC5` |
| `live_notification_tertiary` | `#FF78909C` | `#FF90A4AE` |

锚点：`core/notification/FlymeLiveBackend.kt:isDarkMode()` / `res/color*/live_notification_*.xml`

## 4. 消费者

### 4.1 跑步前台服务（RunTrackingService）

- 注入 `LiveActivityNotifier`
- `startForeground()` 使用 `notifier.buildInitial()` 构建初始通知
- GPS 每次回调后调用 `notifier.show()` 更新距离/时长/速度
- 停止跑步时 `notifier.cancel("running")`
- capsuleText：`"1.23km"`（纯距离）
- 点击路由：`Routes.RUNNING_ACTIVE`

锚点：`feature/running/service/RunTrackingService.kt:59-130`

### 4.2 课程倒计时（CourseNotificationScheduler）

- `@Singleton`，注入 `LiveActivityNotifier`
- 由 `HomeViewModel.init` 启动，挂在 `viewModelScope`
- 每 60 秒检查 `todayCourses`，找到距开始 ≤25 分钟的课程则发布通知
- 课程开始后 5 分钟自动取消
- capsuleText：简化地点（如 `A1S204`，从 `"信息楼（A1）S204"` 提取）
- 点击路由：`Routes.AICLASS_HOME`

锚点：`core/notification/CourseNotificationScheduler.kt:30-101`

## 5. 数据与状态

### 5.1 关键类型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `LiveActivityRequest` | 业务层通用请求 | `LiveActivityModels.kt` |
| `NotificationRoute` | 点击路由目标 | `LiveActivityModels.kt` |
| `LiveActivityPresentation` | 展示形态（Text/Progress） | `LiveActivityModels.kt` |
| `LiveActivityBackend` | backend 接口 | `LiveActivityBackend.kt` |
| `DeviceCapability.Backend` | 设备能力枚举 | `DeviceCapability.kt` |

### 5.2 状态归属

- 通知 ID 映射：`LiveActivityNotifier.keyToId`（内存，进程结束即丢）
- 课程调度状态：`CourseNotificationScheduler.job`（协程 Job，viewModelScope 管理）
- 通知本身：由系统 NotificationManager 持有
- 实况通知权限引导开关：`SharedPreferences("app_prefs")` 中的 `live_notification_enabled`，默认 `true`；当前不作为 `LiveActivityNotifier` 的发送总闸，只影响更多功能页权限状态展示

## 6. 已知约束 / 边界情况

- **通知显示延迟约 10 秒**（Flyme + 原生共有）— `startForeground` 后系统延迟渲染，已通过 `FOREGROUND_SERVICE_IMMEDIATE`（Android 12+）缓解。见 `issues/2026-05-24-live-notification-delay-and-stale`
- **Flyme 后台刷新停滞** — 进入后台约 10 秒后通知内容不再更新（静态字段如距离/速度冻结），但 SystemUI 自驱的 Chronometer 时间可继续走；GPS 数据记录不受影响。根因是 Flyme `isFrozen=4` 进程冻结 + `numRateViolations` 通知限频双重作用。当前接受分层刷新策略，不再引入 AlarmManager 强保活。见 `issues/2026-05-24-live-notification-delay-and-stale`
- **课程通知分钟级延迟** — scheduler 每 60 秒轮询，最坏延迟近 1 分钟；已有课程数据加载后 `checkNow()` 优化首屏
- **Flyme 展开不支持 BigTextStyle** — 与 `is_live=true` extras 冲突导致空白，必须用 RemoteViews
- **channel 创建后 importance 不可修改** — 需卸载重装才能生效新 importance
- **精确闹钟/电池白名单是权限引导，不等同于发送能力判定** — `MoreFeaturesScreen` 展示和跳转系统权限页，`LiveActivityNotifier` backend 选择仍由 `DeviceCapability` 和系统通知能力决定

## 7. 代码锚点

- `core/notification/LiveActivityModels.kt` — 通用模型
- `core/notification/LiveActivityNotifier.kt` — 统一门面
- `core/notification/DeviceCapability.kt` — 设备能力检测
- `core/notification/FlymeLiveBackend.kt` — Flyme 私有实况通知
- `core/notification/AndroidPromotedBackend.kt` — Android 16 promoted
- `core/notification/NormalBackend.kt` — 普通兜底
- `core/notification/CourseNotificationScheduler.kt` — 课程倒计时调度
- `feature/more/ui/MoreFeaturesScreen.kt` — 实况通知权限引导开关 + 精确闹钟/电池优化状态
- `feature/running/service/RunTrackingService.kt` — 跑步消费者
- `feature/home/ui/HomeViewModel.kt` — 课程调度启动点
- `res/layout/notification_live_running.xml` — 跑步 Flyme 展开布局
- `res/layout/notification_live_course.xml` — 课程 Flyme 展开布局
- `res/color/live_notification_title.xml` — 浅色模式主文字色
- `res/color/live_notification_secondary.xml` — 浅色模式次要文字色
- `res/color/live_notification_tertiary.xml` — 浅色模式辅助文字色
- `res/color-night/live_notification_title.xml` — 深色模式主文字色
- `res/color-night/live_notification_secondary.xml` — 深色模式次要文字色
- `res/color-night/live_notification_tertiary.xml` — 深色模式辅助文字色

## 8. 相关文档

- 平台接口契约：`codestable/reference/live-notification-platform-contracts.md`
- 星课表实现分析：`codestable/reference/star-schedule-analysis.md`
- 跑步模块：`codestable/architecture/running-overview.md`
- 首页模块：`codestable/architecture/home-overview.md`
- 课表模块：`codestable/architecture/schedule-overview.md`
- 已知问题：`codestable/issues/2026-05-24-live-notification-delay-and-stale/`
