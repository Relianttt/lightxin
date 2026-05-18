---
doc_type: architecture
slug: exam-overview
scope: feature/exam/ 的考试成绩查询模块：复用 CshRetrofit 查询成绩、学年列表、当前学期
summary: exam 复用 CshRetrofit（in.aiit.edu.cn/zhxy-csh），提供按学年学期查询成绩、客户端计算加权平均绩点的能力
status: current
last_reviewed: 2026-05-19
tags: [exam, score, gpa, csh]
depends_on: [network-overview, auth-overview]
---

# 考试成绩模块总览

## 1. 定位与受众

本 doc 描述 `feature/exam/` 的职责边界：

- 按学年/学期查询考试成绩列表
- 客户端计算总学分与加权平均绩点
- 学年列表与当前学期信息获取

## 2. 结构与交互

### 2.1 三层结构

```
ExamApi (data)            → 3 个接口（复用 @CshRetrofit）
ExamRepository (data)     → 从 TokenManager 读取 userCode，映射响应到领域模型
ExamViewModel (ui)        → 并行加载学年列表+当前学期，切换时重新查询
ExamScreen (ui)           → 学年/学期下拉选择器 + 成绩卡片列表 + 汇总卡片
```

### 2.2 网络层

复用 `@CshRetrofit`（`in.aiit.edu.cn/zhxy-csh`），与课表 `ScheduleApi` 共用同一个 Retrofit 实例。

AuthInterceptor 默认分支会向 FormBody 注入 `access_token`/`_userCode`/`userId` 等通用参数。`stuExamScore.do` 需要的 `userCode` 字段名不在自动注入列表中（注入的是 `_userCode`），因此由 Repository 从 `TokenManager.getUserCode()` 显式传入。

`mobileXsxkKkxns.do` 和 `getXlVo.do` 无业务参数，使用 `@FormUrlEncoded` + `@Field("_") dummy` 触发 AuthInterceptor 注入（与 `ScheduleApi.getWeekList()` 一致）。

### 2.3 成绩字段兼容

`cj`（成绩）字段为 String 类型，兼容两种格式：
- 数字字符串：`"81"`、`"77"`
- 等级字符串：`"优秀"`、`"良好"`

UI 层直接展示原始字符串，不做数值转换。

### 2.4 GPA 计算

客户端从 `hdjd`（获得绩点）和 `xf`（学分）计算加权平均绩点：
- 加权绩点 = Σ(hdjd × xf) / Σ(xf)
- 仅在成绩列表非空时展示汇总卡片

## 3. 数据与状态

| 类型 | 含义 |
|---|---|
| `ExamScore` | courseCode / courseName / department / credit / score / examType / gpa / category / teacher |
| `SchoolYear` | display / value |
| `CurrentTerm` | schoolYear / semester |
| `ExamUiState` | schoolYears / selectedYear / selectedSemester / scores / isLoading / isScoresLoading / error |

## 4. 代码锚点

- `feature/exam/data/ExamApi.kt` — 3 个接口
- `feature/exam/data/ExamResponse.kt` — 响应模型
- `feature/exam/data/ExamRepository.kt` — Repository + DI Module
- `feature/exam/domain/ExamModels.kt` — 领域模型
- `feature/exam/ui/ExamViewModel.kt` — 状态管理
- `feature/exam/ui/ExamScreen.kt` — UI

## 5. 已知约束

- 成绩数据不缓存到本地，每次进入或切换学期都重新请求
- GPA 计算依赖 `hdjd` 字段非空且可解析为 Double；等级制课程若 `hdjd` 为空则不参与计算
- 无补考/重修信息（接口未返回）
