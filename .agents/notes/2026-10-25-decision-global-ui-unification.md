---
doc_type: decision
category: convention
status: active
date: 2026-10-25
tags: [designsystem, theme, recovery, ui]
---

## 背景

用户确认将全局底色与组件统一到 Claude token 层，并反馈登录失效提示太长。本篇修订 [并行 UI 色板](2026-10-18-decision-claude-ui-language.md) 的隔离迁移策略；请求协议、鉴权与课程分类配色不在修改范围。

## 决定

- 旧语义 token 保留名称，作为新 token 的别名；全局 ColorScheme 包括 surface 容器使用同一色板。Raw 色值和课程分类配色不改。
- `LxCard` 保留 16dp 圆角，增加 0.5dp hairline；启动页浅/深色与 LxCanvas 一致。
- 首屏加载用骨架；详情加载三行骨架；局部操作保留进度指示器；分页统一文字页脚与显式错误重试。
- 首页使用 `LxSessionBanner` 白卡与文字操作，登录失效正文为“部分功能已失效，重新登录后恢复”。AI 课堂横幅使用同一卡片而非琥珀底。
- `ServiceErrorCard` 直接放页面底色，删除装饰图标、脚注和多行操作卡。固定提供重试、重新登录、错误详情；重试中前两项都禁用。
- 劳动教育确定登录失效时以重新登录为主；查寝、报修身份拒绝及 AI 课堂恢复以重试为主。技术原文只进详情，不改变各自恢复路径。

## 考虑过的替代方案

- 并行色板隔离迁移：早期能控制影响面，但当前用户明确要求全局统一，改以别名保留旧调用点，避免逐页重写。
- 继续用多段解释和三行操作：信息完整，但用户反馈太长；使用一句说明与主次操作，技术细节按需展开。

## 后果

全局主题会影响未重写的旧页面，需要后续浅/深色截图核对；不改 Typography。P2/P3 已通过 testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest；随后全量单元测试含数据库迁移共 185 项通过。已通过 adb install -r 覆盖安装，登录偏好哈希不变；首页、课表与学工在线完成浅色真机冒烟。测试 APK 仅编译，未在用户设备执行 instrumentation；深色和恢复卡片交互仍待真机核验。劳动教育/素质学分缓存保留与失效横幅属于 P4/T7，尚未实施。

## 成绩与模板操作区补充

- 考试成绩页使用单个学期选择弹层、顶部汇总和无卡片记录行；绩点仅按正学分且有效绩点的课程加权，无有效项显示 `--`。切换学期一次请求，取消旧请求并检查请求代次；重试保留所选学期。
- 模板详情导出和重命名改用 `LxActionRow` 分组，删除降为居中文字操作，保留原有确认弹层；选择 JSON 文件改用主按钮。不改导入导出格式、默认模板和删除逻辑。
- `testDebugUnitTest` 共 186 项通过，`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通过。成绩页空状态与学期弹层已真机查看，用户反馈无明显问题；模板新操作区交互尚未真机核验。新增操作行 UI 测试仅编译，不在用户设备执行 instrumentation。
