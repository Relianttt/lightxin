---
doc_type: reference
status: current
updated: 2026-05-24
summary: Android 16 原生 Live Updates 与 Flyme 私有实况通知的平台接口契约
---

# 实况通知平台接口契约

本文只记录平台/ROM 通知协议，不记录业务编排。feature design 负责说明“什么时候使用哪条分支”，本文负责说明“每条分支必须设置哪些接口字段”。

## 1. Android 16 原生 Live Updates

信息源：

- Android Developers: Create live update notifications  
  `https://developer.android.com/develop/ui/views/notifications/live-update?hl=zh-cn`
- Android Developers: Progress-centric notifications  
  `https://developer.android.com/about/versions/16/features/progress-centric-notifications`
- Android API reference: `Notification.Builder` / `NotificationManager` / `Notification`

### 1.1 适用场景

只用于正在进行、用户发起、时间敏感且有明确开始/结束的活动。例如跑步、导航、网约车、外卖配送。不要用于广告、聊天消息、普通提醒、长期功能入口、尚未开始或已经结束的活动。

### 1.2 版本、依赖与权限

编译和运行前置：

- `compileSdk` 需要覆盖 Android 16 API。
- AndroidX Core 需要使用支持 promoted ongoing API 的版本；优先使用当前项目可用的最新版 `androidx.core:core-ktx`。
- Manifest 必须声明：

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
```

运行时权限：

- Android 13+ 仍需先拿到 `POST_NOTIFICATIONS`。
- `POST_PROMOTED_NOTIFICATIONS` 是 manifest 权限，是否能 promoted 还要看用户设置和通知自身格式。

### 1.3 必要条件

一条通知必须同时满足：

- Android manifest 声明 `android.permission.POST_PROMOTED_NOTIFICATIONS`。
- 请求 promoted ongoing：优先用 `NotificationCompat.Builder#setRequestPromotedOngoing(true)`；也可以写 `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` / `android.requestPromotedOngoing=true`。
- 通知必须是 ongoing：`setOngoing(true)` / `FLAG_ONGOING_EVENT`。
- 必须设置 `contentTitle`。
- 不得设置任何 custom content view，也就是不能使用 `RemoteViews`。
- 不得是 group summary。
- 不得 `setColorized(true)`。
- notification channel 不得是 `NotificationManager.IMPORTANCE_MIN`。
- 通知样式必须是标准样式：`BigTextStyle`、`CallStyle`、`ProgressStyle` 或 `MetricStyle`。

### 1.4 通知频道

推荐为实况通知单独建 channel，不复用普通提醒 channel：

```kotlin
val channel = NotificationChannel(
    LIVE_CHANNEL_ID,
    "LightXin 实况通知",
    NotificationManager.IMPORTANCE_LOW,
).apply {
    description = "正在进行的跑步、签到或课程倒计时"
    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    setShowBadge(false)
}
notificationManager.createNotificationChannel(channel)
```

注意：

- 官方只禁止 `IMPORTANCE_MIN`，没有要求必须 `IMPORTANCE_HIGH`。
- 前台服务类 ongoing 通知建议从 `IMPORTANCE_LOW` 起步，避免变成频繁打扰；需要抬头时另行评估。
- 用户一旦修改 channel 设置，应用后续无法强制覆盖。

### 1.5 能力检测与设置入口

构建通知前后分别检查：

```kotlin
val manager = context.getSystemService(NotificationManager::class.java)
val canPostPromoted = if (Build.VERSION.SDK_INT >= 36) {
    manager.canPostPromotedNotifications()
} else {
    false
}
```

构建出通知后检查格式：

```kotlin
val notification = builder.build()
val promotable = if (Build.VERSION.SDK_INT >= 36) {
    notification.hasPromotableCharacteristics()
} else {
    false
}
```

语义：

- `NotificationManager.canPostPromotedNotifications()`：当前 app 是否允许发布 promoted notification；受用户设置影响。
- `Notification.hasPromotableCharacteristics()`：该通知对象格式是否满足 promoted 条件；不代表用户已经允许。
- `Notification.FLAG_PROMOTED_ONGOING`：系统实际提升后会设置的只读 flag，应用不能主动设置。

用户设置入口：

```kotlin
val intent = Intent(Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS).apply {
    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
context.startActivity(intent)
```

该入口只在 Android 16+ 有意义；低版本不要展示。

### 1.6 Builder 字段

- 使用 `Notification.Builder#setShortCriticalText(String)` 或 `setWhen()` 传递状态栏 chip 的关键信息。
- chip 始终包含小图标，文本很短才会显示完整；实践上应控制在 7 个字符以内。
- 如果使用倒计时/计时器，可配合 `setUsesChronometer()`、`setChronometerCountdown()` 和 `setWhen()`。

推荐字段：

```kotlin
val builder = NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_notification_lightxin)
    .setContentTitle(title)
    .setContentText(content)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setCategory(NotificationCompat.CATEGORY_STATUS)
    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    .setContentIntent(pendingIntent)
```

再请求 promoted：

```kotlin
builder
    .setRequestPromotedOngoing(true)
    .setShortCriticalText(shortText.take(7))
```

兼容兜底：如果当前 AndroidX Core 没有对应编译期 API，可用反射调用 `setRequestPromotedOngoing(true)`，并同时写 extras：

```kotlin
builder.addExtras(
    Bundle().apply {
        putBoolean("android.requestPromotedOngoing", true)
        putString("android.shortCriticalText", shortText.take(7))
    }
)
```

### 1.7 Chip 文本与图标

状态栏 chip 的可见信息来自：

- 小图标：`setSmallIcon()`，必须是适合状态栏的单色通知图标。
- 短文本：`setShortCriticalText()`；太长会被截断或不显示。
- 时间/倒计时：`setWhen()` + chronometer 相关字段。

本项目建议：

| 场景 | shortCriticalText |
|---|---|
| 跑步等待 GPS | `待定位` |
| 跑步中 | `跑步中` 或距离短值 |
| 签到进行中 | `签到` |
| 课程倒计时 | `上课` / `下课` |

不要把完整业务说明塞进 chip；完整文本放 `contentTitle` / `contentText`。

### 1.8 Style 选择

Android 16 Live Updates 只接受标准样式。当前项目建议：

| 场景 | 样式 | 说明 |
|---|---|---|
| 跑步第一版 | 标准文本 + `setProgress()` 或 `BigTextStyle` | 简单稳定，兼容 AndroidX |
| 签到倒计时 | 标准文本 + chronometer | 用 `setWhen()` 表示剩余时间 |
| 课程倒计时 | 标准文本 + chronometer | 用户主动开启时使用 |
| 后续复杂进度 | Android 16 `Notification.ProgressStyle` | 需要 API 36 类型时再接入 |

如果使用 `Notification.ProgressStyle`，它可以描述进度段、点和状态，但第一版不强依赖。引入时要单独确认项目 compileSdk / AndroidX 支持情况，避免为了 style 破坏低版本编译。

### 1.9 推荐实现形态

```kotlin
fun buildAndroidLiveUpdateNotification(
    context: Context,
    request: LiveActivityRequest,
    notificationId: Int,
): Notification {
    val shortText = request.shortCriticalText().take(7)
    val builder = NotificationCompat.Builder(context, request.channelId)
        .setSmallIcon(R.drawable.ic_notification_lightxin)
        .setContentTitle(request.title)
        .setContentText(request.content)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(NotificationRouteIntent.pendingIntent(context, request, notificationId))
        .setRequestPromotedOngoing(true)
        .setShortCriticalText(shortText)

    when (val presentation = request.presentation) {
        is LiveActivityPresentation.Progress -> {
            builder.setProgress(presentation.total, presentation.current, false)
            presentation.label?.let(builder::setSubText)
        }
        is LiveActivityPresentation.Text -> {
            presentation.subtitle?.let(builder::setSubText)
        }
    }

    return builder.build()
}
```

禁止在 Android 原生分支使用：

```kotlin
builder.setCustomContentView(...)
builder.setCustomBigContentView(...)
builder.setCustomHeadsUpContentView(...)
builder.setColorized(true)
builder.setGroupSummary(true)
```

### 1.10 验证点

构建后本地检查：

```kotlin
if (Build.VERSION.SDK_INT >= 36) {
    check(notification.hasPromotableCharacteristics())
}
```

adb 检查：

```text
adb shell dumpsys notification --noredact
```

关注字段：

- `android.requestPromotedOngoing=true`
- `android.shortCriticalText`
- `FLAG_ONGOING_EVENT`
- channel importance 不是 `IMPORTANCE_MIN`
- `contentView=null` / 没有 custom view
- 系统实际 promoted 后可能出现 `FLAG_PROMOTED_ONGOING`

失败时先判断是哪类失败：

| 现象 | 优先检查 |
|---|---|
| 只是普通 ongoing | `canPostPromotedNotifications()`、用户设置、`hasPromotableCharacteristics()` |
| chip 图标空白 | `setSmallIcon()` 是否为通知专用单色图标 |
| chip 不显示文本 | `shortCriticalText` 是否过长，是否被系统选择用 `when` |
| 直接不满足 promoted | 是否用了 custom view、group summary、colorized、IMPORTANCE_MIN、缺 title |

## 2. Flyme 私有实况通知

信息源：

- 星课表：`https://github.com/lightStarrr/starSchedule`
- Flyme Live Notification Demo：`https://github.com/Ruyue-Kinsenka/Flyme-Live-Notification-Demo`
- 本项目真机观察：MEIZU 20 / Flyme 12.6 / Android SDK 36

Flyme 没有可依赖的官方公开文档。本节是基于社区实现和真机日志归纳的私有协议，后续以真机验证为准。

### 2.1 适用条件

- `Build.MANUFACTURER` 为 `meizu`。
- Flyme 版本 >= 11。
- manifest 声明 `flyme.permission.READ_NOTIFICATION_LIVE_STATE`。
- Flyme 实况通知开关返回 enabled。

开关检测参考星课表：

```kotlin
val result = context.contentResolver.call(
    Uri.parse("content://com.android.systemui.notification.provider"),
    "isNotificationLiveEnabled",
    null,
    null,
)
val enabled = result?.getBoolean("result", false) == true
```

不要把这个检测写成普通 `query()`。

### 2.2 外层 live bundle

```kotlin
val liveBundle = Bundle().apply {
    putBoolean("is_live", true)
    putInt("notification.live.operation", 0)
    putInt("notification.live.type", 10)
    putBundle("notification.live.capsule", capsuleBundle)
    putInt("notification.live.contentColor", contentColor)
}
```

已观察到的含义：

| key | 类型 | 说明 |
|---|---|---|
| `is_live` | Boolean | Flyme 实况通知标记 |
| `notification.live.operation` | Int | 操作类型；参考实现发布时为 `0` |
| `notification.live.type` | Int | 实况类型；星课表用 `10`，demo 用过 `2` |
| `notification.live.capsule` | Bundle | 胶囊配置 |
| `notification.live.contentColor` | Int | 展开卡片内容色，应跟随系统深色模式动态选择（浅色 `#FF263238`，深色 `#FFFFFFFF`），不应硬编码 |

### 2.3 capsule bundle

`capsuleBundle` 内部 key 必须使用完整的 `notification.live.capsule*` 形式，不能简写。

```kotlin
val capsuleBundle = Bundle().apply {
    putInt("notification.live.capsuleStatus", 1)
    putInt("notification.live.capsuleType", 3)
    putString("notification.live.capsuleContent", capsuleText)
    putParcelable("notification.live.capsuleIcon", icon)
    putInt("notification.live.capsuleBgColor", bgColor)
    putInt("notification.live.capsuleContentColor", contentColor)
    putParcelable("notification.live.capsule.content.remote.view", capsuleRemoteViews) // 可选
}
```

已观察到的含义：

| key | 类型 | 说明 |
|---|---|---|
| `notification.live.capsuleStatus` | Int | 启用/状态字段；参考实现为 `1` |
| `notification.live.capsuleType` | Int | 胶囊类型；星课表用 `3`，demo 用过 `5` |
| `notification.live.capsuleContent` | String | 胶囊文本 |
| `notification.live.capsuleIcon` | Icon | 胶囊图标 |
| `notification.live.capsuleBgColor` | Int | 胶囊背景色 |
| `notification.live.capsuleContentColor` | Int | 胶囊内容色 |
| `notification.live.capsule.content.remote.view` | RemoteViews | demo 使用的胶囊自定义视图，可选但值得真机验证 |

### 2.4 通知构建要求

Flyme 分支与 Android 原生分支相反：它允许并依赖 `RemoteViews` 来渲染展开卡片。

```kotlin
val notification = Notification.Builder(context, channelId)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle(title)
    .setContentText(content)
    .addExtras(liveBundle)
    .setCustomContentView(contentRemoteViews)
    .setAutoCancel(false)
    .build()
```

Flyme 实况通知频道建议使用 `NotificationManager.IMPORTANCE_HIGH`。星课表和 demo 都使用 high importance 的 live channel。

注意：LightXin 实际使用 `IMPORTANCE_LOW`（前台服务通知避免频繁打扰），与星课表的 HIGH 不一致。若后续需要调试通知优先级，换 channel id 重新创建即可验证。

### 2.5 RemoteViews 文字颜色

Flyme 实况通知的展开卡片背景由系统根据当前主题渲染（浅色/深色），RemoteViews 内的文字不应硬编码颜色，否则深色模式下深字+深底无法辨认。推荐做法：

- 布局中用 `@color/live_notification_*` 引用替代硬编码 `#FFxxxxxx`
- 在 `res/color/` 和 `res/color-night/` 分别定义浅色和深色变体
- `notification.live.contentColor` 在代码中通过 `Configuration.uiMode` 检测深色模式后动态选择

参考实现：星课表 `res/color/live_title_color.xml` + `res/color-night/live_title_color.xml`，以及 `NotificationManager.kt` 中 `autoContentColorFor()` 根据胶囊背景亮度算黑/白。

### 2.6 常见误区

- 不要只写外层 `notification.live.capsule`，内层字段才决定胶囊内容如何渲染。
- 不要把内层 key 写成 `showText`、`topDisplayText`、`capsuleStatus` 这类简写；这些更像 Flyme SystemUI 内部模型字段，不等于外部 extras 协议。
- 不要因为 Android 16 原生分支禁止 `RemoteViews`，就删除 Flyme 分支的 `RemoteViews`。
- 不要把 Android 的 `setRequestPromotedOngoing(true)` 当成 Flyme 私有实况通知的核心条件。

## 3. 分支隔离规则

| 分支 | 必须做 | 禁止做 |
|---|---|---|
| Flyme | `notification.live.*` extras、`RemoteViews`、Flyme 开关检测 | 依赖 Android promoted 作为主要渲染条件 |
| Android 16 原生 | ongoing、request promoted、标准样式、无 custom view | 携带 Flyme 私有字段、使用 `RemoteViews` |
| Normal | 普通可点击通知 | 携带 Flyme 字段或请求 promoted |

任何后续 feature design 只应引用本文，不应在 design 内复制整套平台字段。字段更新时只改本文。
