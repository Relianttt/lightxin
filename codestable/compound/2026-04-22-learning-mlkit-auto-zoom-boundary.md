---
doc_type: learning
status: draft
date: 2026-04-22
track: mlkit-barcode
summary: ML Kit ZoomSuggestionOptions 的自动变焦是软件层建议，只在二维码已被部分检测到时触发；二维码完全检测不到时自动变焦无效，需要靠提高分析分辨率或手动变焦兜底
tags: [mlkit, barcode, scan, zoom, camerax]
---

# ML Kit 自动变焦的触发条件边界

## 现象

扫码签到功能在二维码距离较远、画面中二维码较小时无法识别。最初认为 ML Kit 的 `ZoomSuggestionOptions` 会自动拉近画面来解决这个问题，但实际测试发现：**当 ML Kit 完全检测不到二维码时，自动变焦建议不会被触发**。

## 根因

`ZoomSuggestionOptions` 的行为机制：

1. ML Kit 的 `BarcodeScanner.process()` 在每一帧图像上运行检测
2. 只有当 ML Kit 在图像中**检测到了某个条形码/二维码的候选区域**，但认为它**尺寸太小**时，才会通过 `ZoomCallback` 向 app 发送"建议放大到 X 倍"的信号
3. 如果二维码在画面中太小、像素不够，ML Kit 根本检测不到任何候选区域，则不会触发任何变焦建议

简而言之：**ZoomSuggestionOptions 只能锦上添花（已有检测但嫌小时放大），不能雪中送炭（完全检测不到时无能为力）**。

## 解决方案

采用三层互补手段：

| 层次 | 手段 | 解决场景 |
|---|---|---|
| 第一层 | 提高 `ImageAnalysis` 分辨率至 720p | 让 ML Kit 获得更多像素，提升远距离检测率 |
| 第二层 | ML Kit `ZoomSuggestionOptions` 自动变焦（已有） | 已检测到但嫌小时自动拉近 |
| 第三层 | 双指捏合手动变焦 | 完全检测不到时用户主动拉近（兜底） |

## 关联

- 方案文档：`codestable/features/2026-04-22-manual-zoom-scan/manual-zoom-scan-design.md`
- 决策记录：`codestable/compound/2026-04-22-decision-ml-kit-barcode.md`
