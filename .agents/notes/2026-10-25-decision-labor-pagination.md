---
doc_type: decision
category: architecture
status: active
date: 2026-10-25
tags: [labor, pagination, protocol]
---

## 背景

劳动教育触底后没有新增记录。原 App 的接口分析记录 `pageSize=100`、`total=28`，应用请求大小为 10 且丢弃 `total`。同一 ID 可出现在不同类型中，不能只用 ID 去重。

## 决定

- 请求大小与原 App 对齐为 100；保留响应 `total`，优先按已加载的唯一记录数量判断是否还有下一页。
- 唯一键为 `type:id`，首屏、跨页、同页去重与列表 key 一致；详情仍发送原始 `id` 和 `type`，不把组合键发给服务端。
- 一页没有新记录立即停止，避免服务端忽略页码时重复请求；没有 `total` 才按原始页大小回退判断。
- 合并后的列表按有效活动日期倒序，无法解析的日期放末尾；翻页失败保留内容和页码，只允许用户点击重试，不在触底状态下自动循环重试。
- 仅改变 `pageSize`，不调整身份字段。现有登录失效分类仍见 [登录失效处理](2026-10-17-decision-login-expiry-surfaces.md)。

## 考虑过的替代方案

- 只看是否满页：实现简单，但忽略服务端总数，且无法阻止重复页请求。
- 只按 ID 去重：可以过滤重复页，但会丢掉不同类型下的合法记录。

## 后果

通常一页即可加载全部，但总数超过 100 时仍按页加载。若服务端反复返回重复页，会停止而不假装获取了全部 `total` 条记录。

协议依据：`docs/接口分析/跑步接口深度分析报告.md` 的 `queryTimesDetailsPage.do` 请求与响应记录。尚未完成真实账号的分页诊断：2026-10-25 连接的 Pixel 4 首页显示登录失效，不能据此声称条数与 total 已真机核对。

待抓包确认（本次不改）：`LaborApi` 与 `AuthInterceptor` 重复发送 `userCode/xh/accessToken`；原 App 请求有 `userType`，当前只有 `_userType`。
