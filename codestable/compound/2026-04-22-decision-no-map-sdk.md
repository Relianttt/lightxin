---
doc_type: decision
category: constraint
date: 2026-04-22
slug: no-map-sdk
status: active
area: 跑步 / 定位
tags: [location, running, sdk]
---

## 背景

跑步功能需要 GPS 定位和坐标转换，但不需要在 App 内展示地图轨迹。

## 决定

不引入任何地图 SDK（高德 / 百度 / 腾讯），定位走 Android 原生 `LocationManager`，坐标转换手写（WGS-84 → GCJ-02 → BD-09）。

## 理由

1. 跑步仪表盘不展示地图轨迹，只展示距离/配速/时间等数字指标
2. 地图 SDK（特别是百度定位 SDK）体积大、初始化慢、对包体积和启动速度有明显负面影响
3. 原生 `LocationManager` + 手写坐标转换在当前场景下完全够用

## 考虑过的替代方案

- 引入高德/百度地图 SDK：体积和性能代价远超收益

## 后果

- 定位精度受限于原生 GPS，室内场景可能不精确
- 坐标转换代码（`CoordinateConverter`）需要自行维护和测试
- 如果未来需要展示地图轨迹，需要重新评估是否引入 SDK

## 相关文档

- `core/location/LocationProvider.kt` — 原生 LocationManager 封装
- `core/location/CoordinateConverter.kt` — WGS-84 → GCJ-02 → BD-09
- `codestable/architecture/running-overview.md`
