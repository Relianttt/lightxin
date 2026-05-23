---
doc_type: issue-report
slug: live-notification-delay-and-stale
status: open
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

## 待排查方向

- `startForeground()` 到系统实际渲染通知之间是否有系统级延迟
- Flyme 对 `is_live=true` 通知是否有首次显示的冷启动延迟
- 后台刷新停滞是否为 Flyme ROM 对 `notify()` 调用的节流行为
- logcat 中 `startForeground` 时间戳 vs 通知实际出现时间戳的差值
