---
doc_type: architecture
slug: credit-overview
scope: feature/credit/ 的素质学分模块：新建 CreditRetrofit 查询五模块学分总览、记录列表、记录详情
summary: credit 使用独立 CreditRetrofit（cqc.aiit.edu.cn），复用主 OkHttpClient（AuthInterceptor 默认分支注入多余参数，服务端忽略），提供五模块进度可视化 + 记录列表 + 详情 BottomSheet
status: current
last_reviewed: 2026-05-19
tags: [credit, quality, cqc]
depends_on: [network-overview, auth-overview]
---

# 素质学分模块总览

## 1. 定位与受众

本 doc 描述 `feature/credit/` 的职责边界：

- 五模块（文化创新/学术科技/社会考核/任职贡献/科研活动）学分总览与进度可视化
- 全部学分记录列表（不分页）
- 单条记录详情（获奖级别/等级/时间/所属模块/审核状态）

## 2. 结构与交互

### 2.1 三层结构

```
CreditApi (data)            → 3 个接口（@CreditRetrofit）
CreditRepository (data)     → 从 TokenManager 读取 userCode 作为 studentCode
CreditViewModel (ui)        → 并行加载总览+记录列表，点击记录加载详情
CreditScreen (ui)           → 总览进度条卡片 + 记录列表 + ModalBottomSheet 详情
```

### 2.2 网络层

新建 `@CreditRetrofit`，BASE URL 指向 `https://cqc.aiit.edu.cn/`。

复用主 `OkHttpClient`（含 AuthInterceptor）。AuthInterceptor 默认分支会向 FormBody 注入 `access_token`/`_userCode`/`userId`/`_userName`/`_userType`/`appId` 等通用参数。`cqc.aiit.edu.cn` 不在 AuthInterceptor 的特殊 host 白名单中，命中默认分支。

**关键约束**：服务端忽略多余注入字段。接口真正需要的 `studentCode` 由 CreditApi 显式 `@Field("studentCode")` 声明，值从 `TokenManager.getUserCode()` 读取。

### 2.3 记录列表

`findCreditRecord.do` 返回全部记录（不分页），UI 层直接用 LazyColumn 渲染。记录数量通常 < 30 条，无需客户端分页。

## 3. 数据与状态

| 类型 | 含义 |
|---|---|
| `CreditOverview` | totalCredit / pass / modules: List<CreditModule> |
| `CreditModule` | name / type / credit |
| `CreditRecord` | id / name / score / statusName |
| `CreditRecordDetail` | name / highestLevelName / awardLevelName / awardPrizeName / prizeScore / getTime / qualityModuleName / qualityCategoryName / statusName |
| `CreditUiState` | overview / records / selectedDetail / isLoading / isDetailLoading / showDetailSheet / error |

## 4. 代码锚点

- `feature/credit/data/CreditApi.kt` — 3 个接口
- `feature/credit/data/CreditResponse.kt` — 响应模型
- `feature/credit/data/CreditRepository.kt` — Repository + DI Module
- `feature/credit/domain/CreditModels.kt` — 领域模型
- `feature/credit/ui/CreditViewModel.kt` — 状态管理
- `feature/credit/ui/CreditScreen.kt` — UI（含 OverviewCard / CreditBarRow / RecordCard / DetailSheetContent）

## 5. 已知约束

- 复用主 OkHttpClient 的 AuthInterceptor 会注入多余字段到 FormBody，依赖"服务端忽略"假设
- 数据不缓存，每次进入页面重新请求
- 记录列表失败时优先展示错误信息而非空态（已修复）
