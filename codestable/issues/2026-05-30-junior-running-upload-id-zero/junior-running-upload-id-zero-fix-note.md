---
doc_type: issue-fix
issue: 2026-05-30-junior-running-upload-id-zero
status: implemented
severity: P1
summary: 大一大二跑步上传返回 id=0 时不再误报成功，并修正大二今日里程数据源
tags: [running, junior-grade, upload, sports-api]
---

# Junior Running Upload Id Zero Fix Note

## 问题现象

大二年级跑步数据上传后，结果页显示上传成功，但记录 ID 为 `0`；返回跑步首页查看今日里程仍为 `0`。

## 根因

1. `RunningRepository.uploadSnapshot()` 只判断 `result == OK`，没有把 `data.id=0` 视为服务端未生成有效跑步记录，因此会出现“假成功”。
2. 大一大二的今日里程来自 `extraDetailInfo.do.data.extraDetail.todayMile`，当前首页仍从 `startIndex.do` 的 `todayKM/todayMile` 读取，导致大二今日里程显示为 0。

## 修复

- `RunningRepository.getDashboard()` 在大一大二分支并行读取 `extraDetailInfo.do`，优先使用其中的 `todayMile / completeMile / surplusMile / maxMile / maxMileDate / mixOnceMile`。
- `RunningRepository.uploadSnapshot()` 要求上传响应同时满足 `result=OK` 且 `id` 非空、非 `0`、非 `null` 才算成功；否则返回失败文案，避免误导用户。
- `SportsGradeMapper.parseExtraDetail()` 扩展为解析大一大二详情模型，并补充单测覆盖。

## 验证

- `.\gradlew.bat :app:testDebugUnitTest --tests com.lightxin.feature.running.data.SportsGradeMapperTest`：通过。
- `.\gradlew.bat lint`：通过。

## 备注

已尝试通过 adb 读取当前设备日志；设备在线，但现有 logcat 缓冲区没有保留到本次 `addExtraCheckNew.do` 上传请求/响应。后续若服务端仍返回 `id=0`，结果页会明确展示失败，可继续用现场抓包定位服务端拒收的具体字段。
