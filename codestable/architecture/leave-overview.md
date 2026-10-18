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
| 待办任务节点 | `mobile/flowRuTask/getRunTaskByUserCode.do {approvedType:3}` |
| 销假提交 | `mobile/process/agree.do`（dataJson / executionId / ruTaskNodeId / stuQjsq） |
| 附件 | `file/upload.do`（multipart） |

所有响应以 `JsonObject` 接收，`LeaveRepository` 递归容错提取；`flag/result/code` 任一成功语义都接受。

### 2.3 表单模型

`LeaveFormField(key, label, type, required, readonly, options, order)`，类型枚举：

`TEXT_INPUT / TEXTAREA / RADIO / SELECT / DATE_RANGE / ADDRESS / UPLOAD_FILE / UPLOAD_IMG / FORMULA / READONLY_TEXT / UNKNOWN`

- 解析器 `LeaveFormSchemaParser`：key/label/type 多候选归一；递归展开 `card/grid/columns/list` 容器，跳过 `properties/rules/formula` 内部副本；选项优先读 `properties.options`；`rules[].required` / `properties.disabled` / `properties.hidden` 生效；按 order 排序
- 装配器 `LeaveFormBuilder`：默认值 → 用户输入 → 日期区间数组 → 附件对象数组 → `QJSC` 重算 → 身份字段
- 校验器 `LeaveFormValidator`：required、原因 ≥10 字、结束时间晚于开始时间；就地展示

### 2.4 UI 流程

- `LeaveListScreen`：页头随列表滚动，滚动后导航栏出现“请假”小标题；分段 tab（进行中 / 全部，计数取服务端 `total`，进行中扣掉已撤回）；按“2026 年 6 月”分组，行首“01 周一” + 标题 + 信息行（状态 · 提交时间）；主动作“发起请假”是右下 FAB。标题从 `flowTitle` 提取已知请假类型（病假/事假等）显示为「病假申请」，取不到时回落「学生请假申请」
- `LeaveCreateScreen`：显示顺序为 类型 → 起止时间 → 是否离校 → 事由 → 联系人与地址 → 附件（只调显示，提交字段不变）；起止时间在同一个 `LxDateRangeSheet` 里选（默认下一个整点、5 分钟步进、快捷时刻、结束须晚于开始）；说明 / 审批规则收成一行可展开；顶部暂存 + 底部固定提交
- `LeaveDetailScreen`：状态块 + 三步进度 + 时间块 + 字段网格（值常规字重、长值独占行）；待销假时超假信息紧跟状态块（带单位），销假通过待办节点 + `agree.do` 提交；撤回走底部确认弹层
- 提交成功 `popBackStack()`，列表通过 `savedStateHandle["leave_refresh"]` 刷新

真机校准（2026-10-18）：

- `getFlowSheet.data.flowSheet` 和 `getForm.data.formJson` 是字符串，需解包
- `getForm.do` 必须用 `type=0`，`type=1 + 空 ruTaskNodeId` 会挂起到超时
- 列表 `status=1` 进行中 / `status=3` 全部
- `getXjxx.do` 返回展示信息（`cjScStr/isCj/...`）而非可回写表单；销假不在本地回写它，而是取待办节点后 `agree.do`
- `myExecution.do` 的 `status=1` 含“已撤回”，客户端在“进行中”里剔除；列表按 `receiveTime` 倒序，列表行没有 `flowNumber`

`ADDRESS` 只做纯文本输入 —— 遵守项目「无地图 SDK」硬约束，不做地点选择器。

拍照入口对 `ActivityNotFoundException` 做兜底（提示从相册选择）；本机 Pixel 4 / Android 13 仍无法拉起系统相机，属已知问题，见 `.agents/notes/2026-10-18-learning-camera-intent-not-found.md`。

## 3. 数据与状态

| 类型 | 含义 | 位置 |
|---|---|---|
| `LeaveSummary` | 列表项（executionId / flowTitle / statusTxt / receiveTime / ruTaskNodeId） | `data/LeaveModels.kt` |
| `LeavePage` | 分页信封（items + 服务端 total） | 同上 |
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
- 销假需要“待销假”待办节点（`ruTaskNodeId`）；拿不到时按钮不出现，提示返回列表刷新
- `getXjxx.do` 的 `cjSc` 是小时数（3342 → 139 天），界面优先显示 `cjScStr` 原文并补单位

## 6. 相关文档

- 鉴权：`codestable/architecture/network-overview.md`
- 导航：`codestable/architecture/navigation-overview.md`
- 决策：`.agents/notes/2026-10-18-decision-leave-dynamic-form.md`
