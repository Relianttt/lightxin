---
doc_type: learning
track: pitfall
status: active
date: 2026-10-25
tags: [androidTest, device, data-safety]
---

## 现象

Pixel 4 在运行 `connectedDebugAndroidTest` 前已安装 LightXin，首页有用户信息与登录失效横幅。6 项 Compose 测试成功后，启动 MainActivity 报 Activity 不存在，`adb shell pm list packages com.lightxin` 无输出。重新安装 Debug APK 后出现空白登录页；原本本地数据是否完整保留未验证。

## 根因

设备测试包含 APK 部署与清理，并不只是执行测试方法。此次未隔离用户设备上的同包名应用，也未预先确认当前 Gradle 测试任务的卸载/保留数据行为。具体是哪一层清理配置触发卸载尚未追踪，不能假设测试成功即代表用户数据安全。

## 解法

已重新安装构建出的 Debug APK，但这只能恢复应用安装，不能证明恢复了原数据。已告知用户当前需要重新登录及数据保留情况未知。

## 预防

- 优先在可清空的模拟器或专用测试设备执行 Gradle instrumentation。
- 必须使用已有用户数据的设备时，先核实部署/卸载策略与数据保留方式，并征得对实际清理风险的确认；不要把“允许跑测试”理解为“允许清除应用数据”。
- 编译测试 APK 与运行真机测试分开报告，无法确保数据安全时仅编译，不以 UI 测试要求为由直接执行安装清理流程。
