---
doc_type: architecture
slug: location-overview
scope: core/location/ 的原生定位封装与坐标转换工具，以及查寝/跑步对 WGS-84 → BD-09 的消费边界
summary: location 不引入地图 SDK，只用 Android LocationManager 暴露权限检查、最后已知位置和 GPS Flow；CoordinateConverter 在客户端完成 WGS-84 → GCJ-02 → BD-09 转换，供查寝签到和跑步上传使用。
status: current
last_reviewed: 2026-05-05
tags: [location, gps, coordinate, wgs84, gcj02, bd09]
depends_on: [checkin-overview, running-overview]
---

# 定位基础层总览

## 0. 术语

- **WGS-84** — Android 原生定位返回的坐标系，`LocationProvider` 直接产出 `android.location.Location`。
- **GCJ-02** — WGS-84 到 BD-09 的中间坐标系，`CoordinateConverter.wgs84ToGcj02()` 内部使用。锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:27-39`。
- **BD-09** — 服务端期望的百度坐标系；查寝提交和跑步上传前通过 `CoordinateConverter.wgs84ToBd09()` 转换。锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:21-25`。
- **LocationProvider** — 对 Android `LocationManager` 的轻量封装，只负责权限检查、最后已知位置和 GPS 位置流。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:18-70`。

## 1. 定位与受众

本 doc 描述 `core/location/` 的职责边界：

- 使用 Android 原生 `LocationManager` 读取定位，不引入地图 SDK。
- 提供快速定位（last known）和持续 GPS Flow 两种入口。
- 提供 WGS-84 → GCJ-02 → BD-09 坐标转换。
- 说明查寝、跑步两个消费者如何使用定位基础层。

不覆盖：

- 查寝详情页的拍照/提交表单状态；见 `codestable/architecture/checkin-overview.md`。
- 跑步前台服务、轨迹质量、路线模板或模拟提交；见 `codestable/architecture/running-overview.md`。
- Android 运行时权限弹窗 UI；权限由调用方页面/服务负责触发，`LocationProvider` 只检查当前是否已授予。

适用读者：

- 要排查“定位为空 / 没权限 / 坐标偏移”的人。
- 要新增依赖定位的 feature，并判断是否该复用当前基础层的人。
- 要确认项目为什么没有地图 SDK 的人。

## 2. 结构与交互

### 2.1 两个文件分工

| 文件 | 职责 |
|---|---|
| `LocationProvider.kt` | 封装 `LocationManager`，提供 `hasLocationPermission()`、`getLastKnownLocation()`、`locationUpdates()`。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:18-70` |
| `CoordinateConverter.kt` | 手写坐标转换：WGS-84 → GCJ-02 → BD-09，返回轻量 `LatLng`。锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:12-69` |

### 2.2 LocationProvider 的三种入口

| 方法 | 行为 | 锚点 |
|---|---|---|
| `hasLocationPermission()` | 只检查 `ACCESS_FINE_LOCATION` 是否已授予 | `app/src/main/java/com/lightxin/core/location/LocationProvider.kt:25-29` |
| `getLastKnownLocation()` | 无权限直接返回 null；有权限时先取 GPS_PROVIDER，失败再取 NETWORK_PROVIDER | `app/src/main/java/com/lightxin/core/location/LocationProvider.kt:31-40` |
| `locationUpdates(intervalMs, minDistanceM)` | 用 `callbackFlow` 包装 GPS_PROVIDER 的 `requestLocationUpdates()`；取消收集时 `removeUpdates(listener)` | `app/src/main/java/com/lightxin/core/location/LocationProvider.kt:42-69` |

`locationUpdates()` 只请求 `LocationManager.GPS_PROVIDER`，没有同时订阅 NETWORK_PROVIDER；`getLastKnownLocation()` 才有 GPS → Network 的 fallback。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:35-36,53-59`。

### 2.3 CoordinateConverter 的转换链

主入口 `wgs84ToBd09(lat, lng)` 分两步：

```
WGS-84 → wgs84ToGcj02() → GCJ-02 → gcj02ToBd09() → BD-09
```

锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:21-25`。

实现边界：

- `wgs84ToGcj02()` 对中国范围外坐标直接返回原坐标。锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:27-39,66-68`。
- `gcj02ToBd09()` 使用 BD 偏移公式生成 `LatLng(bdLat, bdLng)`。锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:41-48`。
- `LatLng` 只承载 `latitude / longitude`，不绑定 Android Location 或地图 SDK 类型。锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:19`。

### 2.4 查寝消费：先快取，失败再订阅持续定位

`CheckinDetailViewModel` 注入 `LocationProvider`，定位流程是：

1. `getLastKnownLocation()` 先尝试快速拿缓存位置。
2. 若为空，订阅 `locationUpdates(intervalMs = 2000L, minDistanceM = 0f)`。
3. 每次收到 WGS-84 位置后调用 `CoordinateConverter.wgs84ToBd09()`。
4. 将最新 BD-09 坐标写入提交字段。

锚点：`app/src/main/java/com/lightxin/feature/checkin/ui/CheckinDetailViewModel.kt:46,82-104`。

该模块解释“为什么查寝上传看到的是 BD-09 而不是 Android 原生坐标”：转换发生在 UI ViewModel 内，基础层只提供转换函数。

### 2.5 跑步消费：服务采集 WGS-84，上传前转 BD-09

跑步有两处消费 location 基础层：

- `RunTrackingService` 注入 `LocationProvider`，以 `locationUpdates(intervalMs = 3000L, minDistanceM = 2f)` 采集真实跑步轨迹。锚点：`app/src/main/java/com/lightxin/feature/running/service/RunTrackingService.kt:28,64`。
- `RunningRepository` 在上传前把轨迹点转换为 BD-09。锚点：`app/src/main/java/com/lightxin/feature/running/data/RunningRepository.kt:150`。

因此实时记录保存的轨迹点和最终提交给服务端的坐标系不是同一层职责：采集在 service，坐标转换在 data/repository 上传链路。

## 3. 数据与状态

### 3.1 LocationProvider 不持久化状态

`LocationProvider` 是 `@Singleton`，但不缓存位置、不维护权限状态、不持久化任何坐标。每次调用都读取系统服务或注册 listener：

- 单例声明：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:18-20`。
- `locationManager` 是每次从 `Context.LOCATION_SERVICE` 获取的属性 getter。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:22-23`。
- Flow 取消时移除 listener。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:65-67`。

### 3.2 坐标模型只在转换工具内定义

`CoordinateConverter.LatLng` 是轻量 data class：

| 字段 | 含义 |
|---|---|
| `latitude` | 纬度 |
| `longitude` | 经度 |

锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:19`。

它避免让基础层暴露地图 SDK 类型，也避免把 Android `Location` 作为转换结果传到数据层。

## 4. 关键决策

- **不引入地图 SDK** —— 当前定位基础层只用 Android 原生 `LocationManager` 和数学转换；没有地图视图或地图 SDK 类型进入 core/location。来源：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:3-10,22-23` / `codestable/compound/2026-04-22-decision-no-map-sdk.md:15-18`。
- **坐标转换放客户端完成** —— 服务端期望 BD-09，Android 原生定位返回 WGS-84；上传前由客户端转 WGS-84 → GCJ-02 → BD-09。来源：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:8-24`。
- **定位采集与坐标转换分离** —— `LocationProvider` 只产出系统 `Location`，不暗中转换；调用方明确在提交/上传链路调用 `CoordinateConverter`。来源：`app/src/main/java/com/lightxin/feature/checkin/ui/CheckinDetailViewModel.kt:82-104` / `app/src/main/java/com/lightxin/feature/running/data/RunningRepository.kt:150`。
- **权限检查只做读，不发起权限请求** —— `LocationProvider.hasLocationPermission()` 只返回当前状态；权限弹窗属于页面或服务调用方职责。来源：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:25-29,43-48`。

## 5. 代码锚点

- `app/src/main/java/com/lightxin/core/location/LocationProvider.kt:18-23` — 单例与系统 `LocationManager` 获取。
- `app/src/main/java/com/lightxin/core/location/LocationProvider.kt:25-29` — `ACCESS_FINE_LOCATION` 检查。
- `app/src/main/java/com/lightxin/core/location/LocationProvider.kt:31-40` — 最后已知位置 GPS → Network fallback。
- `app/src/main/java/com/lightxin/core/location/LocationProvider.kt:42-69` — GPS Flow 与 listener 生命周期。
- `app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:19-25` — `LatLng` 与 WGS-84 → BD-09 主入口。
- `app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:27-48` — WGS-84 → GCJ-02 → BD-09 两段转换。
- `app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:50-68` — 中国范围判断与转换辅助函数。
- `app/src/main/java/com/lightxin/feature/checkin/ui/CheckinDetailViewModel.kt:82-104` — 查寝定位与转换消费。
- `app/src/main/java/com/lightxin/feature/running/service/RunTrackingService.kt:64` — 跑步真实模式位置流消费。
- `app/src/main/java/com/lightxin/feature/running/data/RunningRepository.kt:150` — 跑步上传前 BD-09 转换。

## 6. 已知约束 / 边界情况

- **只检查细定位权限** —— `hasLocationPermission()` 只看 `ACCESS_FINE_LOCATION`，不检查 `ACCESS_COARSE_LOCATION`。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:25-29`。
- **持续定位只订阅 GPS_PROVIDER** —— `locationUpdates()` 不使用 Network provider fallback。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:53-59`。
- **无权限时两条路径行为不同** —— `getLastKnownLocation()` 返回 null；`locationUpdates()` 关闭 Flow 并抛出 `SecurityException("缺少定位权限")`。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:31-39,43-48`。
- **基础层不做定位超时** —— Flow 会持续等系统回调；超时或 UI 提示由调用方处理。锚点：`app/src/main/java/com/lightxin/core/location/LocationProvider.kt:42-69`。
- **中国范围外不做 GCJ 偏移** —— `wgs84ToGcj02()` 对范围外坐标直接返回原坐标，再进入 GCJ→BD 转换入口的后续逻辑。锚点：`app/src/main/java/com/lightxin/core/location/CoordinateConverter.kt:27-29,66-68`。

## 7. 相关文档

- 查寝定位提交链路：`codestable/architecture/checkin-overview.md`。
- 跑步真实模式与上传链路：`codestable/architecture/running-overview.md`。
- 不引入地图 SDK 决策：`codestable/compound/2026-04-22-decision-no-map-sdk.md`。
- 架构总入口：`codestable/architecture/DESIGN.md`
