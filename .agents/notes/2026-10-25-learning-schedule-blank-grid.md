---
doc_type: learning
track: pitfall
status: active
date: 2026-10-25
tags: [schedule, parser, cache]
---

## 现象

重新登录后课表显示“该周尚未缓存，联网后可获取 / 课程节次无效”。同步已触发，但解析失败使整周无法写入 Room。

## 根因

只读使用当前登录态探测 `selectStuSelfTimeTable.do`：2026 学年第一学期第 8 周返回 `flag=true`，91 个网格记录的课程名称、开始节次、结束节次等字段均为空字符串，仅 `sort` 非空。第 1、7、9 周也返回相同空白结构。这是无课网格占位，不是节次格式损坏的真实课程。解析器把每个占位都当课程校验，因 `ksjc=""` 拒绝整周。

## 解法

`ScheduleParser.toScheduleData` 仅跳过课程名、起止节次均为明确空字符串且其他已映射课程字段为空的占位项；保留日期，允许保存已加载的空周。缺失必要字段、有课程名却无节次、缺名称但有节次等残缺数据仍拒绝，不以跳过异常课程掩盖丢课。

这补充 [离线课表缓存决策](2026-10-21-decision-offline-schedule-ai-prewarm.md) 的“成功空周”语义，不改变账号隔离、缓存格式或请求字段。

## 预防

- `ScheduleParserTest` 覆盖 7×13 空格、空格与真实课程混排、残缺课程拒绝。
- `ScheduleCacheTest` 验证空白网格可缓存、损坏课程不能覆盖已存快照。
- 2026-10-25：课表 data 包 15 项 JVM 测试、`lintDebug`、`assembleDebug` 通过。使用 `adb install -r` 覆盖安装前后登录文件摘要一致；设备显示“上次同步 10月25日 19:47 / 本周没有课程”，不再报节次无效。没有执行卸载、清数据或 instrumentation。
