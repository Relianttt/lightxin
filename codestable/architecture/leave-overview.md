---
doc_type: architecture
slug: leave-overview
scope: 请假模块，覆盖 ywlz 工作流鉴权、后端驱动表单渲染、提交/暂存、我的申请列表、详情、撤回与销假
summary: leave 以 LeaveRepository + LeaveFormSchemaParser 为核心，把服务端 getFlowSheet 的字段定义渲染成 Compose 控件；dataJson 二次序列化提交，附件先走 file/upload.do；列表/详情/撤回/销假闭环，未知字段原样回传
status: current
last_reviewed: 2026-10-18
tags: [leave, ywlz, workflow, dynamic-form, retrofit]
depends_on: [network-overview, navigation-overview]
---

# 请假模块总览

## 0. 术语

- **ywlz** — 学校工作流引擎域名 `ywlz.aiit.edu.cn`，业务前缀 `/zhxy-bfc`
- **flowSheet** — `getFlowSheet.do` 返回的表单定义；字段数组可能是 `fields / formFields / list / flowSheet`
- **dataJson** — 服务端要求的二次序列化 JSON 字符串，作为普通表单字段提交
- **表单驱动** — 控件类型、顺序、必填、选项都来自 flowSheet，而不是本地硬编码
- **未知字段** — 本地没有映射到渲染类型的字段；降级为可编辑文本并保留 key 参与提交，避免丢字段

## 1. 定位与受众

`feature/leave/` 提供：

- 发起请假（动态表单 + 暂存 + 提交）
- 我的申请列表（进行中 / 全部，分页 20）
- 请假详情（只读回显）
- 撤回（`getMyRequestButtons` 判定后）
- 销假（状态含「待销假」时读 `getXjxx.do` 并提交）

读者：要改请假表单渲染、接入新工作流、或排查提交丢字段/鉴权失败的人。

## 2. 结构与交互

### 2.1 鉴权与 Retrofit

- `ApiConstants.BASE_LEAVE = https://ywlz.aiit.edu.cn/zhxy-bfc`
- `NetworkModule.@LeaveRetrofit` 复用主 `OkHttpClient`
- `AuthInterceptor` 的 `ywlz.aiit.edu.cn` 分支：FormBody 追加 `accessToken / userCode / xh / _userCode / _userType`，补 `Origin`、`Referer: https://ywlz.aiit.edu.cn/mobile/`、`X-Requested-With: XMLHttpRequest`；**不加** `appId / _userName / access_token`

### 2.2 接口面

| 用途 | 端点 |
|---|---|
| 流程定义 | `mobile/flowRuTask/getFlowDefinitionsplus.do` |
| 表单结构 | `mobile/process/getFlowSheet.do` |
| 默认值 | `mobile/process/findDefinitionBase.do` |
| 暂存 / 提交 | `mobile/process/saveForm.do` / `mobile/process/submitForm.do` |
| 我的申请 | `mobile/flowRuTask/myExecution.do` |
| 表单回显 | `mobile/process/getForm.do` |
| 可撤回 | `mobile/process/getMyRequestButtons.do` |
| 撤回 | `mobile/flowRuTask/withdrawExecution.do` |
| 销假信息 | `mobile/process/getXjxx.do` |
| 附件 | `file/upload.do`（multipart） |

所有响应以 `JsonObject` 接收，`LeaveRepository` 递归容错提取；`flag/result/code` 任一成功语义都接受。

### 2.3 表单模型

`LeaveFormField(key, label, type, required, readonly, options, order)`，类型枚举：

`TEXT_INPUT / TEXTAREA / RADIO / SELECT / DATE_RANGE / ADDRESS / UPLOAD_FILE / UPLOAD_IMG / FORMULA / READONLY_TEXT / UNKNOWN`

- 解析器 `LeaveFormSchemaParser`：key/label/type 多候选归一；递归展开 `card/grid/columns/list` 容器，跳过 `properties/rules/formula` 内部副本；选项优先读 `properties.options`；`rules[].required` / `properties.disabled` / `properties.hidden` 生效；按 order 排序
- 装配器 `LeaveFormBuilder`：默认值 → 用户输入 → 日期区间数组 → 附件对象数组 → `QJSC` 重算 → 身份字段
- 校验器 `LeaveFormValidator`：required、原因 ≥10 字、结束时间晚于开始时间；就地展示

### 2.4 UI 流程

- `LeaveListScreen`：状态 chip（3 进行中 / 1 全部）、下拉刷新、触底加载、FAB 发起
- `LeaveCreateScreen`：按字段类型渲染；日期区间用 DatePicker + TimePicker 两段；附件支持文件/相册/拍照；底部暂存 + 提交
- `LeaveDetailScreen`：状态卡 + 只读字段 + 撤回 + 销假
- 提交成功 `popBackStack()`，列表通过 `savedStateHandle["leave_refresh"]` 刷新

真机校准（2026-10-18）：

- `getFlowSheet.data.flowSheet` 和 `getForm.data.formJson` 是字符串，需解包
- `getForm.do` 必须用 `type=0`，`type=1 + 空 ruTaskNodeId` 会挂起到超时
- 列表 `status=1` 进行中 / `status=3` 全部
- `getXjxx.do` 返回展示信息而非可回写表单，只在含 `QJRQ/dataJson/flowTitle` 时显示提交按钮

`ADDRESS` 只做纯文本输入 —— 遵守项目「无地图 SDK」硬约束，不做地点选择器。

## 3. 数据与状态

| 类型 | 含义 | 位置 |
|---|---|---|
| `LeaveSummary` | 列表项（executionId / flowTitle / statusTxt / receiveTime） | `data/LeaveModels.kt` |
| `LeaveDetail` | 详情（字段 + dataJson 解析值） | 同上 |
| `LeaveFormField` | 渲染字段定义 | `domain/LeaveForm.kt` |
| `LeaveFormInput` | 用户输入（文本 / 日期区间 / 附件） | 同上 |
| `LeaveCreateUiState` | 表单加载、错误、上传中、提交态 | `ui/LeaveViewModels.kt` |

## 4. 关键决策

- 全后端驱动渲染，未知字段降级可编辑并回传（见 `.agents/notes/2026-10-18-decision-leave-dynamic-form.md`）
- `dataJson` 二次序列化，附件空值提交 `""`（与抓包一致）
- 撤回与销假都要服务端信号，不在本地猜测
- `definitionId` 按名称匹配，失败兜底已知值

## 5. 已知约束 / 边界

- `getFlowSheet` / `myExecution` / 销假响应结构已在真机校准；后续服务端改结构时先看 `LeaveJson.parseSchema` 与 `LeaveFormSchemaParser`
- `ADDRESS` 不接地图；用户需手填地点
- 附件单文件上限 10MB（本地提示，服务端上限未验证）
- 销假信息不是扁平表单时只展示、隐藏提交按钮

## 6. 相关文档

- 鉴权：`codestable/architecture/network-overview.md`
- 导航：`codestable/architecture/navigation-overview.md`
- 决策：`.agents/notes/2026-10-18-decision-leave-dynamic-form.md`
