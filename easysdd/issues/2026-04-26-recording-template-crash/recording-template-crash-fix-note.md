---
doc_type: issue-fix
issue: 2026-04-26-recording-template-crash
path: fast-track
fix_date: 2026-04-26
tags: [android, foreground-service, permission, crash]
---

# 录制模板点击崩溃 修复记录

## 1. 问题描述

点击"录制模板" → "开始录制"按钮，App 立即崩溃。

## 2. 根因

`RunTrackingService.kt:52` 的 `startTracking()` 调用 `startForeground()` 时，服务声明了 `foregroundServiceType="location"`。Android 15 (targetSDK=35) 对此类型要求运行时已授予 `ACCESS_FINE_LOCATION` 或 `ACCESS_COARSE_LOCATION`，仅 Manifest 声明不足。

录制模板入口 `RouteTemplateRecordScreen.kt` 在启动服务前未检查运行时权限状态，直接调用 `RunTrackingService.start()` → `startForegroundService()` → `onStartCommand()` → `startForeground()`，触发 `SecurityException`。

（正常跑步入口 `RunningHomeScreen` 已有权限检查，不受影响。）

## 3. 修复方案

- **UI 层** (`RouteTemplateRecordScreen.kt`)：点击"开始录制"时先 `checkSelfPermission`，未授予则通过 `rememberLauncherForActivityResult` 弹出系统权限请求弹窗
- **ViewModel 层** (`RouteTemplateViewModel.kt`)：新增 `setError()` 方法，权限被拒时显示错误提示
- **Service 层** (`RunTrackingService.kt`)：`startForeground()` 前增加 `hasLocationPermission()` 防御性校验，无权限时 `stopSelf()` 优雅退出

## 4. 改动文件清单

| 文件 | 改动 |
|---|---|
| `app/.../ui/RouteTemplateRecordScreen.kt` | 新增权限检查与请求弹窗逻辑 |
| `app/.../ui/RouteTemplateViewModel.kt` | 新增 `setError()` 方法 |
| `app/.../service/RunTrackingService.kt` | 新增 `hasLocationPermission()` 防御性校验 |

## 5. 验证结果

- 无位置权限时点击"开始录制" → 弹出系统权限弹窗，不崩溃
- 拒绝权限 → 显示"缺少定位权限，无法开始录制"错误提示
- 授予权限 → 正常进入录制流程，GPS 开始采集

## 6. 遗留事项

无。
