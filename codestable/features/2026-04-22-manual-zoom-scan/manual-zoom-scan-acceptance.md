---
doc_type: acceptance
feature: manual-zoom-scan
slug: manual-zoom-scan
status: draft
date: 2026-04-22
summary: 扫码签到手动变焦功能验收报告——ImageAnalysis 分辨率提升至 720p + 双指捏合缩放，三层手段解决远距离小二维码识别问题
tags: [aiclass, scan, camera, zoom, mlkit, pinch-to-zoom]
---

# 扫码签到变焦能力增强 — 验收报告

## 1. 改动范围

单文件改动：`feature/aiclass/ui/AiClassScanScreen.kt`
- 新增 `import android.util.Size`、`import android.view.ScaleGestureDetector`
- `ImageAnalysis.Builder` 追加 `.setTargetResolution(Size(1280, 720))`
- `CameraPreview` 函数签名新增 `onCameraReady: (Camera) -> Unit = {}`（扩展预留，当前未在上层消费）
- `PreviewView` 设置 `ScaleGestureDetector` + `OnTouchListener` 实现双指捏合缩放
- 缩放上限与自动变焦统一：`minOf(hardwareMax, OFFICIAL_AUTO_ZOOM_MAX_RATIO)`

其他更新：
- `app/build.gradle.kts` — release 构建限定 arm64-v8a
- `codestable/architecture/aiclass-overview.md` — §2.7 补充手动捏合变焦能力
- `codestable/features/2026-04-22-manual-zoom-scan/manual-zoom-scan-design.md` — §2.2 回调说明对齐实际实现

## 2. 检查项验证

| 检查项 | 状态 | 验证方式 |
|---|---|---|
| check-1: 远距离二维码识别率提升（1m+） | passed | 真机实测通过 |
| check-2: 双指缩放后扫码功能正常 | passed | 真机实测通过 |
| check-3: 手动与自动变焦上限一致 | passed | 共用 `minOf(hardwareMax, 4f)` 计算逻辑 |
| check-4: 不支持变焦的设备无崩溃 | passed | `zoomState.value ?: return false` 兜底 |
| check-5: ML Kit 自动变焦建议仍正常工作 | passed | `ZoomSuggestionOptions` 代码未改动 |
| check-6: aiclass-overview.md §2.7 更新 | passed | 已补充手动捏合变焦说明 |

## 3. 复审问题记录

### AC-01（中风险，已接受）：分辨率降级路径缺失
- **位置**：`AiClassScanScreen.kt` try-catch 块
- **现象**：`setTargetResolution(Size(1280, 720))` 在极端机型上可能失败，当前仅记录日志
- **决定**：已知风险，保留当前实现。后续可补降级到默认分辨率 + 用户可见提示

### AC-02（已修复）：触摸事件拦截
- **位置**：`AiClassScanScreen.kt` `OnTouchListener`
- **修复**：改为返回 `scaleDetector.onTouchEvent(event)` 结果，手势不消费时事件可继续传播

### AC-03（已修复）：设计文档与实际实现偏移
- **位置**：`manual-zoom-scan-design.md` §2.2
- **修复**：注释改为"扩展预留，当前手势在 CameraPreview 内部实现"

## 4. 构建验证

- `./gradlew assembleDebug` — BUILD SUCCESSFUL
- `./gradlew assembleRelease` — BUILD SUCCESSFUL（含 R8 混淆 + 单 arm64-v8a）

## 5. 结论

**通过**。无阻断项，两个中风险项（AC-01 已接受、AC-02 已修复），一个低风险项（AC-03 已修复）。
