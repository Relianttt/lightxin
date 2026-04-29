---
doc_type: feature-design
feature: manual-zoom-scan
slug: manual-zoom-scan
status: approved
date: 2026-04-22
summary: 通过提高 ImageAnalysis 分辨率 + 双指捏合缩放 + 保留 ML Kit 自动变焦，三层手段解决二维码较远/较小无法识别的问题
tags: [aiclass, scan, camera, zoom, mlkit, pinch-to-zoom]
---

# 扫码签到变焦能力增强

## 0. 术语

- **ImageAnalysis 分辨率** — `ImageAnalysis.Builder.setTargetResolution()` 设定的分析图像尺寸。当前未设置，CameraX 使用默认协商分辨率（机型相关，通常为 480p~720p 区间）。分辨率越高，ML Kit 可分析的像素越多，识别远处小二维码的能力越强
- **变焦比 / ZoomRatio** — CameraX 的缩放系数，1.0 = 未缩放（广角），值越大放大倍数越高；上限取决于设备硬件能力
- **ML Kit 自动变焦建议** — ML Kit 的 `ZoomSuggestionOptions`：当 ML Kit 检测到二维码但嫌太小时，**向 app 代码发送建议**，由 app 的 `zoomCallback` 决定是否执行放大。当前已实现
- **双指捏合缩放 / Pinch-to-Zoom** — 用户用双指在预览画面上捏合/张开来控制缩放，通过 `ScaleGestureDetector` + `CameraControl.setZoomRatio()` 实现

## 1. 决策与约束

### 1.1 功能定位

本 feature 属于 `feature/aiclass/ui/AiClassScanScreen.kt` 的 UI 层增强，不涉及 data/domain 层改动，不涉及 ViewModel 改动。

### 1.2 三层手段，互补而非替代

| 层次 | 手段 | 解决场景 | 状态 |
|---|---|---|---|
| **第一层** | 提高 `ImageAnalysis` 分辨率至屏幕尺寸 | ML Kit 获得更多像素，**提升远距离二维码的检测率**（根因修复） | 新增 |
| **第二层** | ML Kit `ZoomSuggestionOptions` 自动变焦（已有） | ML Kit 已检测到但嫌小时自动拉近 | 已有，不变 |
| **第三层** | 用户双指捏合手动变焦 | ML Kit 完全检测不到时，用户主动拉近（兜底） | 新增 |

### 1.3 明确不做

- 不做 +/- 变焦按钮——手势更自然，无 UI 侵入
- 不做滑块变焦
- 不改 ML Kit 的 `BarcodeScannerOptions` 配置——当前 `FORMAT_QR_CODE` + 自动变焦已是最优
- 不改扫码签到流程的其他部分（302 处理、token 提取、SSO 等）

### 1.4 假设

- CameraX `PreviewView` 的 `OnTouchListener` 和扫码检测的 `ImageAnalysis.Analyzer` 不冲突（不同线程）
- 提高分辨率不会在低端设备上导致显著性能问题（`STRATEGY_KEEP_ONLY_LATEST` 保证不会积压帧）
- 双指捏合缩放和 ML Kit 自动变焦建议不会互相冲突（`setZoomRatio()` 是幂等的）

## 2. 关键接口

### 2.1 ImageAnalysis 分辨率调整

```kotlin
// 当前（CameraX 默认协商分辨率，机型相关）：
val analysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()

// 改为（匹配屏幕尺寸）：
// 来源：AiClassScanScreen.kt CameraPreview 内，约第 217 行
val analysis = ImageAnalysis.Builder()
    .setTargetResolution(Size(1280, 720))  // ← 新增：720p 是精度与性能的平衡点
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
```

选择 720p 的理由：720p（1280×720）相比默认协商分辨率能提供更多像素，显著提升检测距离；1080p 收益递减且对低端设备有性能压力。最终收益以真机实测识别率与识别耗时为验收指标。

### 2.2 CameraPreview 新增回调 + pinch-to-zoom（内部实现）

```kotlin
// 新增：相机就绪时回调，将 Camera 实例暴露给父组件（扩展预留）
// 当前手势缩放直接在 CameraPreview 内部实现，父组件未消费此回调
// 来源：AiClassScanScreen.kt 的 CameraPreview 函数
@Composable
private fun CameraPreview(
    onQrCodeDetected: (String) -> Unit,
    onCameraReady: (Camera) -> Unit = {},  // ← 新增：扩展预留，当前未在上层消费
)
```

`PreviewView` 的 AndroidView 内部增加 `ScaleGestureDetector`：

```kotlin
// PreviewView factory lambda 内（伪代码，示意逻辑）
// 来源：AiClassScanScreen.kt CameraPreview，约第 204 行
// 手动与自动共用同一 maxZoom 计算逻辑：minOf(硬件上限, 4f)
// 与 AiClassScanScreen.kt 中 applyOfficialZoomSuggestion 的 maxSupportedZoomRatio 一致
val maxSupportedZoom = minOf(
    camera.cameraInfo.zoomState.value?.maxZoomRatio ?: OFFICIAL_AUTO_ZOOM_MAX_RATIO,
    OFFICIAL_AUTO_ZOOM_MAX_RATIO,
)
val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
        val newZoom = (currentZoom * detector.scaleFactor)
            .coerceIn(1f, maxSupportedZoom)
        camera.cameraControl.setZoomRatio(newZoom)
        return true
    }
})
previewView.setOnTouchListener { _, event ->
    scaleDetector.onTouchEvent(event)
    true
}
```

### 2.3 状态流转

```
用户双指张开 → ScaleGestureDetector.onScale()
  → newZoom = currentZoom * scaleFactor
  → coerceIn(1f, maxZoom)
  → camera.cameraControl.setZoomRatio(newZoom)
  → PreviewView 实时缩放

ML Kit 自动变焦建议 → applyOfficialZoomSuggestion()（已有，不变）
  → 独立于手势缩放，两者都调用 setZoomRatio()，互不冲突
```

### 2.4 边界情况

| 场景 | 表现 |
|---|---|
| 设备不支持变焦（`zoomState` 为 null） | 捏合手势无效果，无崩溃 |
| 已达最大变焦 | 继续张开会卡在 maxZoom，不会超出 |
| 已在 1.0x（未缩放） | 继续捏合会卡在 1.0，不会变鱼眼 |
| 低端设备 + 720p 分析 | `STRATEGY_KEEP_ONLY_LATEST` 自动丢帧保性能 |

## 3. 实现提示

### 3.1 推进顺序

1. **提高 ImageAnalysis 分辨率至 720p**（修改：`AiClassScanScreen.kt` 第 ~218 行 `ImageAnalysis.Builder` 追加 `.setTargetResolution(Size(1280, 720))`）
   - 退出信号：扫码页打开后，远处小二维码的识别率明显提升（真机验证）

2. **CameraPreview 新增 `onCameraReady` 回调**（修改：`AiClassScanScreen.kt` CameraPreview 函数追加参数，在 `cameraProvider.bindToLifecycle()` 成功后调用 `onCameraReady(camera)`）
   - 退出信号：CameraPreview 的调用方能收到 Camera 实例

3. **PreviewView 增加双指捏合缩放**（修改：`AiClassScanScreen.kt` CameraPreview 的 AndroidView factory lambda，在 PreviewView 上设置 `OnTouchListener` + `ScaleGestureDetector`）
   - 退出信号：扫码页中双指捏合/张开能控制预览画面缩放

4. **边界验证**
   - 验证提高分辨率后扫码仍然正常触发
   - 验证缩放后扫码正常触发
   - 验证快速缩放不会卡顿
   - 退出信号：所有边界情况通过验证

### 3.2 测试设计

| 功能点 | 验证方式 | 关键用例 |
|---|---|---|
| 远距离检测 | 真机，二维码距离 1m+ | 提高分辨率后远距离识别率提升、识别耗时下降（真机实测） |
| 双指缩放 | 真机，双指张合 | 预览画面实时缩放，zoomRatio 变化 |
| 缩放后扫码 | 放大后对准二维码 | 二维码被正常检测并触发签到流程 |
| 自动变焦仍工作 | 真机，中等距离 | ML Kit 检测到偏小时自动拉近（与手势共存） |
| 低端设备 | 旧机型 | 扫码流畅，无明显卡顿 |
| 不支持变焦的设备 | 模拟器/低端机 | 手势无效果，无崩溃 |

### 3.3 高风险实现约束

- **手势与分析的线程关系**：`ScaleGestureDetector` 在主线程处理触摸事件，`ImageAnalysis.Analyzer` 在独立 executor 线程运行。两者天然不冲突。
- **setTargetResolution 不是精确值**：CameraX 会选择最接近的可用分辨率，不一定是精确的 1280×720。这是 CameraX 的正常行为。
- **ML Kit 自动变焦和手势缩放不冲突**：两者最终都调用 `camera.cameraControl.setZoomRatio()`，最后一次调用生效。用户手动缩放后 ML Kit 自动变焦仍可能触发，但这对功能无负面影响。
- **PreviewView 触摸事件拦截**：如果 `setOnTouchListener` 与 PreviewView 内部的焦点/点击事件冲突，需要在 listener 返回适当值。

### 3.4 要改的文件清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `feature/aiclass/ui/AiClassScanScreen.kt` | 修改 | ImageAnalysis 加分辨率 + CameraPreview 增参 + PreviewView 加手势 |

单文件改动。

## 4. 与项目级架构文档的关系

本 feature 在 `feature/aiclass/ui/AiClassScanScreen.kt` 内部，不涉及跨模块变更。但新增手动捏合缩放属于扫码页能力边界变化，`aiclass-overview.md` 中已记录扫码页能力说明（§2.7），需要在 acceptance 阶段同步更新该节，补充手动捏合缩放作为第三层变焦手段。`DESIGN.md` 无需更新。
