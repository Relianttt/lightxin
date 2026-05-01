---
doc_type: decision
category: tech-stack
date: 2026-04-22
slug: compose-native-rewrite
status: active
area: 全局
tags: [compose, webview, native, rewrite]
---

## 背景

原"爱信校园"APP 采用 H5 + WebView 壳的混合架构，体验笨重、启动慢、无法充分利用原生能力。轻小信的目标是提供轻量、精致的替代体验。

## 决定

全 Compose 原生重写，不保留任何 WebView 壳。所有 UI 使用 Kotlin + Jetpack Compose 实现。

## 理由

1. Compose 声明式 UI 开发效率高，单 Activity 架构简洁
2. 原生渲染性能远优于 WebView，冷启动和页面切换更快
3. 与原 APP 的 H5 + 原生壳模式做根本区别，确立轻小信的定位

## 考虑过的替代方案

- 继续用 H5 + WebView 壳：体验天花板低，和原 APP 无差异
- Flutter：跨平台收益对本项目（只做 Android）无意义

## 后果

- 所有 UI 都是 Compose 组件，不存在 WebView 页面
- 接口协议必须与原 APP 抓包一致（不能自创接口），但 UI 实现完全自由
- 只面向 Android 平台，没有 iOS 版本

## 相关文档

- `AGENTS.md` — 项目结构说明
- `codestable/architecture/DESIGN.md` §1
