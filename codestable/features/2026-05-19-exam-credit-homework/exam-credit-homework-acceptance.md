---
doc_type: feature-acceptance
feature: exam-credit-homework
slug: exam-credit-homework
summary: 新增"更多功能"入口页 + 考试成绩 / 素质学分 / AI课堂作业三个模块的完整实现验收
status: accepted
date: 2026-05-19
tags: [exam, credit, homework, more-features, izuoye]
---

# 更多功能与新模块 验收报告

> 阶段：验收闭环
> 验收日期：2026-05-19
> 关联规划文档：`docs/项目规划/更多功能与新模块开发计划.md`

## 1. 接口契约核对

对照规划文档 Task Breakdown 逐项核查：

**考试成绩（CshRetrofit 复用）**：
- [x] `stuExamScore.do`：显式 `@Field("userCode")` + `kkxn` + `kkxq`，与 ScheduleApi 同模式
- [x] `mobileXsxkKkxns.do`：`@FormUrlEncoded` + dummy field 触发 AuthInterceptor
- [x] `getXlVo.do`：同上 dummy field 模式
- [x] Repository 从 `TokenManager.getUserCode()` 读取 userCode 传给 API

**素质学分（新建 CreditRetrofit）**：
- [x] `findPersonCredit.do`：显式 `@Field("studentCode")`
- [x] `findCreditRecord.do`：显式 `@Field("studentCode")`
- [x] `findCreditRecordDetail.do`：`@Field("id")` + `@Field("studentCode")`
- [x] CreditRetrofit 复用主 OkHttpClient，AuthInterceptor 默认分支注入多余参数，服务端忽略（已加注释说明）

**AI课堂作业（FifRetrofit + 新建 IzuoyeRetrofit）**：
- [x] `listStudent`：GET，FIF Cookie + Authorization 认证
- [x] `getHomeworkDetaisIndexUrl`：GET，返回含 jtzy token 的 URL
- [x] `getTeaWorkDetail`：POST，jtzy Header 认证
- [x] `getStuWorkList`：POST，jtzy Header 认证
- [x] `getStorageId` + `submitWork`：POST，jtzy Header 认证，提交闭环完整

## 2. 行为与决策核对

**需求摘要逐项验证**：
- [x] "更多功能"入口在"我的"页面功能列表卡片最下方
- [x] 劳动教育从"我的"页面移除，仅在"更多功能"中有入口
- [x] "更多功能"页面列表式布局（单卡多行）
- [x] 考试成绩：两个下拉菜单（学年+学期）+ 成绩列表 + 底部汇总
- [x] 素质学分：五模块进度条可视化 + 记录列表 + 详情 BottomSheet
- [x] AI课堂作业：课程详情页内与测验平级的作业 section
- [x] 作业详情页：HTML 渲染用 `AnnotatedString.fromHtml()`
- [x] 提交按钮仅未截止时显示（deadline 时间判断）

**关键决策落地**：
- [x] HTML 用 `AnnotatedString.fromHtml()` — Compose 原生 API，无 WebView
- [x] 素质学分新建 CreditRetrofit — 新域名 `cqc.aiit.edu.cn`
- [x] AI作业新建 IzuoyeRetrofit — 独立域名 + 独立 jtzy JWT 认证
- [x] 考试成绩复用 CshRetrofit — 与课表共用 `in.aiit.edu.cn/zhxy-csh`

**挂载点盘点**：
- `Routes.kt`：新增 MORE_FEATURES / EXAM_SCORES / CREDIT_OVERVIEW / AICLASS_HOMEWORK_DETAIL
- `NavGraph.kt`：注册 4 条新路由 + 更新 AICLASS_DETAIL 传入 onOpenHomeworkDetail
- `ProfileScreen.kt`：移除 onNavigateLabor，新增 onNavigateMore
- `HomeScreen.kt`：更新 ProfileScreen 调用
- `NetworkModule.kt`：新增 @CreditRetrofit / @IzuoyeRetrofit 注解和 Provider
- `ApiConstants.kt`：新增 BASE_CREDIT / BASE_IZUOYE
- `AiClassApi.kt`：新增 2 个接口
- `AiClassResponse.kt`：新增作业响应模型
- `AiClassRepository.kt`：注入 IzuoyeApi，新增作业方法
- `AiClassViewModel.kt`：UiState 扩展 + openCourseDetail 并行加载作业
- `AiClassCourseDetailScreen.kt`：新增作业 section + HomeworkCard

## 3. 验收场景核对

- [x] **S1**：点击"更多功能"进入新页面，三个入口（劳动教育/考试成绩/素质学分）可正常跳转
  - 证据：编译通过 + 导航路由注册完整
- [x] **S2**：考试成绩页切换学年/学期可刷新成绩列表
  - 证据：ViewModel onYearSelected/onSemesterSelected 触发 loadScores
- [x] **S3**：素质学分页展示五模块进度条 + 点击记录弹出详情 BottomSheet
  - 证据：CreditScreen OverviewCard + ModalBottomSheet 实现完整
- [x] **S4**：课程详情页作业列表与测验列表并行加载
  - 证据：openCourseDetail 中两个独立 viewModelScope.launch
- [x] **S5**：作业详情页可查看 HTML 题目、学生提交列表，未截止时可提交纯文本
  - 证据：AiHomeworkDetailScreen + isDeadlinePassed 判断 + SubmitBottomSheet
- [x] **S6**：AI课堂课程卡片能保留理论 / 实验区分，不把同名课程错误合并
  - 证据：`AiClassRepository.getCourses()` 从 `className/classNames/classNameStr` 兜底解析课程类型，`AiClassCourseMergerTest` 覆盖解析逻辑，用户已验证显示正常

## 4. 术语一致性

- `ExamScore` / `SchoolYear` / `CurrentTerm` — 考试成绩领域模型，命名清晰
- `CreditOverview` / `CreditModule` / `CreditRecord` / `CreditRecordDetail` — 素质学分领域模型
- `AiHomework` / `AiHomeworkDetail` / `AiStudentWork` — 作业领域模型，与已有 `AiQuiz` 平级
- `IzuoyeApi` / `IzuoyeRetrofit` — 爱作业平台，与 `FifRetrofit` 命名风格一致
- `jtzy` — 爱作业平台 JWT token 的 Header 名，保持原始协议命名
- `typeName` / `className` / `classNames` / `classNameStr` — AI课堂课程类型与教学班显示字段；理论 / 实验后缀优先取 `typeName`，为空时从教学班显示字段解析

## 5. 架构归并

- [x] `aiclass-overview.md`：归并作业子系统（IzuoyeApi / IzuoyeRetrofit / jtzy 认证 / 作业详情页）
- [x] `aiclass-overview.md`：归并理论 / 实验课程类型解析与稳定 ID 去重约束
- [x] `network-overview.md`：归并 CreditRetrofit / IzuoyeRetrofit 两个新 Retrofit 实例
- [x] 新建 `exam-overview.md`：考试成绩模块架构文档
- [x] 新建 `credit-overview.md`：素质学分模块架构文档

## 6. requirement 回写

本次新增三个用户可感能力（考试成绩查询 / 素质学分查看 / AI课堂作业提交），但项目 `codestable/requirements/` 当前为空（仅 .gitkeep）。按现有项目惯例暂不 backfill requirement——项目历史上从未写过 requirement 文档，保持一致。

## 7. roadmap 回写

非 roadmap 起头，跳过。

## 8. AGENTS.md 候选盘点

- 候选 1：`AGENTS.md` 项目结构描述中应补充 `feature/exam/`、`feature/credit/`、`feature/more/` 三个新模块目录。建议放 AGENTS.md。

## 9. 遗留

- AI课堂作业附件上传未实现（需额外抓取 OSS 上传接口）
- AI课堂作业评论/互评功能未实现
- 素质学分 CreditRetrofit 复用主 OkHttpClient 的 AuthInterceptor 注入行为依赖"服务端忽略多余字段"假设，需抓包确认
- 作业 jtzy token 有效期约 23 天，当前按 cwId+teachClassId 缓存，跨作业切换时清除；极端情况下 token 过期需要重新获取（未做自动刷新）
