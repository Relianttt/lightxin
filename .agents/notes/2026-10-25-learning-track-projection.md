---
doc_type: learning
track: knowledge
status: active
date: 2026-10-25
tags: [running, track, projection, compose]
---

## 背景

跑步轨迹缩略图、详情大图和跑步中实时轨迹都只画形状，不接地图 SDK。原来的 `TrackCanvas` 直接把经纬度等比投影到画布，在纬度 31° 附近经度 1° 对应的实际距离约是纬度 1° 的 cos(31°)≈0.857，形状被横向拉宽约 17%。

## 做法

- 投影前按轨迹中心纬度修正经度跨度：`k = cos(toRadians((minLat + maxLat) / 2))`；经度跨度与 x 位移都乘 `k`，纬度不变。
- 投影抽成 `internal fun projectTrack(points, width, height): List<Offset>`，Canvas 只负责画线、起点和终点，测试直接对纯函数断言。
- 跑步进行中 `live = true`：终点是强调色实心点 + 半透明光晕；结果页仍用红色终点。模拟页和模板列表共用同一投影，形状一致。
- 单测用一个“以米计的正方形”点集（纬度跨度 dLat，经度跨度 dLat / cos(lat)）断言投影后宽高比约为 1，并断言所有点落在画布内。

## 注意

- 快照/模板里的点可能是 BD-09 或 WGS-84，两者的差异在整个轨迹尺度上只是整体平移，不影响形状；只为显示形状时不需要再转换。
- 轨迹点数在几千量级，实时重绘不需要抽稀。

## 后果

模板列表、模板详情、跑步中、结果页和模拟页的轨迹形状一致。验证：`TrackProjectionTest` 通过；跑步中与结果页的真机录屏尚未提供。
