---
doc_type: decision
category: tech-stack
date: 2026-04-22
slug: ml-kit-barcode
status: active
area: AI 课堂扫码
tags: [aiclass, barcode, ml-kit, zxing]
---

## 背景

AI 课堂需要扫描二维码完成课堂签到。Android 生态下主流方案是 ZXing 和 Google ML Kit。

## 决定

使用 Google ML Kit Barcode Scanning，不使用 ZXing。

## 理由

1. ML Kit 是官方方案，集成简单、API 现代，不需要额外引入 `CaptureActivity`
2. ZXing 库体积较大且需要更多胶水代码
3. ML Kit 支持离线扫描，不依赖网络

## 考虑过的替代方案

- ZXing（Zebra Crossing）：最成熟的开源扫码库，但集成复杂度更高、库体积更大

## 后果

- 依赖 Google Play Services（ML Kit 可独立运行，但部分设备可能受限）
- 扫码 UI 需要自行用 CameraX 构建，不依赖 ZXing 的 `CaptureActivity`

## 相关文档

- `feature/aiclass/ui/` — 扫码相关 UI
- `docs/项目规划/AI课堂开发规划.md`
