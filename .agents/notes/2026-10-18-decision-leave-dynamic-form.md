---
doc_type: decision
category: architecture
status: active
date: 2026-10-18
tags: [leave, ywlz, dynamic-form, workflow]
---

## 背景

请假走学校 `ywlz/zhxy-bfc` 工作流引擎。抓包只覆盖到 `submitForm.do`，表单结构与默认值接口未完整捕获；服务端字段以 `F<timestamp>` 动态键为主，硬编码表单会在流程改版时整体失效。

## 决定

- **表单全后端驱动**：`getFlowSheet.do` 返回的字段定义决定控件顺序、标签、必填与选项；本地只负责类型映射与渲染。
- 类型映射容错：`input/textarea/radio/select/dateRange/address/uploadFile/uploadImg/formula/text`；未知类型降级为可编辑文本（`UNKNOWN`），保留 key 回传，避免提交丢字段。
- 字段数组与键名容错：`fields / formFields / list / flowSheet`；key 候选 `name/key/fieldName/field/id`；label 候选 `label/title/text/fieldTitle`；选项候选 `options/optionList/dicts/items`。
- `dataJson` 以字符串二次序列化后作为普通表单字段提交；日期区间存 `["yyyy-MM-dd HH:mm","..."]`；附件存 `{uid,status:"done",fileName,name,fileUrl,url}` 数组，空附件提交 `""`。
- `QJSC` 由 `QJRQ` 计算（小时，整点去小数、否则 1 位），不信任服务端默认值。
- `yxdm/yxmc/bjdm` 等身份字段即使不在表单里也随提交带上。
- 附件上传 `file/upload.do` 使用 multipart，额外附 `userCode` / `accessToken` 文本段；单文件 ≤ 10MB。
- 鉴权：`ywlz` 分支只注入 `accessToken/userCode/xh/_userCode/_userType`，**不加** `appId/_userName/access_token`；补 `Origin`、`Referer: https://ywlz.aiit.edu.cn/mobile/`、`X-Requested-With`。
- 首版提供提交 + 暂存 + 列表（进行中/全部）+ 详情 + 撤回 + 销假；撤回按钮只在 `getMyRequestButtons.withdraw == true` 时出现，销假只在状态含「待销假」时出现。
- **真机校准（2026-10-18）**：
  - `getFlowSheet.do` 的 `data.flowSheet` 与 `getForm.do` 的 `data.formJson` 都是**再包一层字符串**，必须先 `JsonParser` 解包再解析字段。
  - `getForm.do` 用 `type=1` 且 `ruTaskNodeId` 为空时服务端会挂起到超时；改 `type=0` 正常返回，详情状态从 `myExecution.do` 回查补齐。
  - `myExecution.do` 的 `status=1` 是「进行中/未完成」（含待销假、已撤回），`status=3` 才是全部；列表默认 1。
  - `getXjxx.do` 只接受 `id=<executionId>`，返回 `cjScStr/isCj/...` 展示信息，不是可回写表单。
  - 2026-10-18 补：销假的真实入口不是回写 `getXjxx`，而是从待办列表 `getRunTaskByUserCode.do {approvedType:3}` 取 `ruTaskNodeId` 后 `POST agree.do`；详见 `.agents/notes/2026-10-18-decision-mobile-first-ui-revision.md`。

## 理由

- 动态渲染是唯一能跟上流程改版的方案；硬编码字段在换学期/换流程时必坏。
- 未知字段回传比"过滤掉不认识的字段"安全：服务端如果校验必填，过滤会直接导致提交失败。
- 销假接口结构未验证，`getXjxx.do` 返回非扁平对象时只展示、隐藏提交按钮，避免把不确定结构回写。

## 考虑过的替代方案

- **本地硬编码请假表单**：字段少、实现快，但流程一改版（新增字段、改必填）就整体失效，不采用。
- **过滤掉不认识的字段**：提交体更干净，但服务端若校验必填会直接失败；改为未知字段降级渲染并原样回传。
- **WebView 打开学校 H5**：最省事，但与原生导航、登录态、附件体验割裂，也无法复用现有设计系统；只把它当作接口结构的参考。
- **本地自行判断可撤回 / 可销假**：状态文案会变，本地判断容易错；改为以 `getMyRequestButtons` 和 `statusTxt` 为准。

## 后果

- 表单体验受服务端字段质量影响：没有 label 的字段会显示 key；未知控件降级为文本框。
- `definitionId` 按名称「学生请假申请」动态匹配，失败时兜底 `e77db6c3ae0a4911a42280f98ce45ebc`。
- 真实端点/字段仍需真机校准；只改 `LeaveApi` 定义，不动 UI。

## 相关文档

- `codestable/architecture/leave-overview.md`
- 代码：`feature/leave/domain/LeaveForm.kt`、`feature/leave/data/LeaveRepository.kt`
