---
doc_type: learning
track: pitfall
date: 2026-04-26
slug: android15-fgs-location-needs-runtime-permission
component: RunTrackingService
severity: high
tags: [android, foreground-service, permission, crash, target-sdk-35]
---

# Android 15 前台服务 location 类型需要运行时位置权限

## 1. 问题

targetSDK=35 时，启动 `foregroundServiceType="location"` 的前台服务仅凭
Manifest 声明 `FOREGROUND_SERVICE_LOCATION` + `ACCESS_*_LOCATION` 不够，
系统还要求运行时已授予位置权限。

## 2. 症状

`startForeground()` 处抛 `SecurityException`，App 直接崩溃：

```
Starting FGS with type location callerApp=... targetSDK=35 requires permissions:
allOf=true [FOREGROUND_SERVICE_LOCATION]
anyOf=false [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION]
and the app must be in the eligible state/exemptions to access the foreground only permission
```

## 3. 没用的做法

- 在 Manifest 里把权限声明全加上——声明了但运行时没授予，一样崩
- try-catch 包住 `startForeground()`——崩溃发生在系统层校验阶段，App 进程内捕获不到

## 4. 解法

**入口层**：启动前台服务前先 `checkSelfPermission` 确认位置权限已授予，未授予走标准权限请求流程。

**服务层**：在 `startForeground()` 前加防御性 `hasLocationPermission()` 校验，无权限时 `stopSelf()` 优雅退出。

## 5. 为什么有效

Android 15 在 `ActiveServices.validateForegroundServiceType` 做硬性校验，没有运行时位置权限直接拒绝，不走回调。App 侧唯一选择是"启动前确保权限已到位"。

## 6. 预防

- 所有启动 `foregroundServiceType="location"` 前台服务的入口都要做运行时权限检查
- Manifest 里 `FOREGROUND_SERVICE_LOCATION` + `android:foregroundServiceType="location"` ≠ 运行时安全
