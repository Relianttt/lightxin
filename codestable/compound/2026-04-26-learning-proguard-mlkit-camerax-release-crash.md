---
doc_type: learning
track: pitfall
severity: high
component: [camerax, mlkit, proguard]
title: Release 包 ProGuard 缺少 CameraX/ML Kit keep 规则导致扫码失效和启动崩溃
date: 2026-04-26
tags: [proguard, r8, camerax, mlkit, barcode, release]
---

# Release 包 ProGuard 缺少 CameraX/ML Kit keep 规则

## 现象

- Debug 包扫码正常（能识别、能变焦）
- Release 包扫码完全无法识别二维码，画面清晰但 ML Kit 无任何响应
- 补充了部分 ML Kit keep 规则后，release 包**启动即崩溃**：`MlKitInitProvider` 初始化时 DI 解析失败（`Unsatisfied dependency: class bh0`）

## 根因

同一个 commit（a53a8a5）同时做了两件事：加手动变焦 + **开启 release 混淆**（`isMinifyEnabled = true`）。`proguard-rules.pro` 里只有 Retrofit/Gson/OkHttp 的规则，完全没有 CameraX 和 ML Kit 的 keep。

1. **扫码失效**：`ImageProxy.image` 是 `@ExperimentalGetImage` API，底层走内部类/反射路径，被 R8 优化后持续返回 null —— 所有帧被静默丢弃
2. **启动崩溃**：ML Kit 内部用轻量 DI 框架（dagger-like），混淆后接口名如 `bh0`、`zzg` 等不匹配，`MlKitInitProvider` 在 ContentProvider 初始化阶段直接炸

## 解法

`proguard-rules.pro` 补三条规则：

```proguard
# CameraX — release 包扫码必须保留
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# ML Kit — 必须 keep 整个树，内部 DI 在启动时解析所有组件
-keep class com.google.mlkit.** { *; }
```

**注意**：ML Kit 不能只 keep `barcode`/`common` 子包，`MlKitInitProvider` 是全局入口，启动时解析全部已注册组件的依赖链，缺任何一环都崩。

## 预防

- 开启 `isMinifyEnabled` 的 commit 应该**单独做**，不要和功能改动混在一起
- 每次加新的 SDK 依赖时（特别是含 ContentProvider 或有内部 DI 的库），顺手检查 proguard 规则是否覆盖
