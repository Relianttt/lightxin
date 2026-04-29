---
doc_type: feature-design
feature: aiclass-course-quiz-detail
slug: aiclass-course-quiz-detail
status: approved
date: 2026-04-22
summary: 为 AI课堂新增“我的课程”卡片进入课程详情页的能力，并补齐当前学期漏课与详情页测验列表展示
tags: [aiclass, course-detail, quiz, fif, navigation]
---

# AI课堂课程详情与测验列表

## 0. 术语

- **课程详情页** — 本次新增的 Compose 页面，承接“我的课程”课程卡片点击后的二级页面，负责展示课程基础信息与测验列表
- **测验列表** — 以 `paper/getPublishPaperListOfStudent` 的学生可见试卷列表为主，确保历史测验与提交态完整；同时尝试从 `paper/getPaperInCourseInfo` 补齐课程详情页试卷信息。本次只展示标题、发布时间、时长与提交状态
- **课表补数** — 当 `myClassroom(termYear, term)` 未返回当前学期全部课程时，额外使用 FIF `liveAndRecord/timetableInfo` 提取课程条目，补进“我的课程”列表，但不单独新增课表页面
- **FIF memberId** — FIF SSO token 的 JWT payload 中的 `memberId`，本次作为测验接口所需 `userId` 使用
- **已提交状态** — 优先取 `iscommited`，不单独依赖 `status`；来源：`compound/2026-04-22-explore-aiclass-quiz-submit-flow.md`

## 1. 决策与约束

### 1.1 功能定位

本 feature 放在现有 `feature/aiclass/` 内扩展，不新建独立模块。原因：

- 入口仍然是 AI课堂首页里的“我的课程”列表
- 测验数据来自 FIF 同一套会话与仓储
- 导航与状态适合继续复用 `AiClassViewModel`

### 1.2 关键决策

| 决策 | 方案 | 原因 |
|---|---|---|
| 详情入口 | 课程卡片改为可点击，进入 `AICLASS_DETAIL/{classId}` | 与现有 `checkin/labor/running` 的列表到详情交互一致 |
| 详情数据来源 | 课程基础信息沿用首页已加载的 `AiCourse`，测验列表进入详情页后单独拉取 | 避免为本轮需求额外补整套课程详情主接口 |
| 课程列表来源 | `myClassroom(termYear, term)` 为主，`liveAndRecord/timetableInfo` 为当前学期补数兜底 | 用户已观察到原版 H5 的“我的课程”并不覆盖全部当前学期课程，但课表里能看到缺失课程 |
| 测验接口 | 以 `paper/getPublishPaperListOfStudent` 为主，并合并 `paper/getPaperInCourseInfo` 的补充字段 | 用户实测 H5 测验页可看到历史测验，且接口分析已确认学生列表接口本身可覆盖历史记录与提交态；课程详情接口只作为补充，不再提前截断列表 |
| `userId` 来源 | 从 FIF token 的 JWT payload 解出 `memberId` 并持久化 | 当前代码没有单独存 `userId`，但 HAR 已证明 token 内含 `memberId` |
| 课表补数详情标识 | 对补进来的课，详情/测验请求使用 `teachClassId(classId)`，不直接用课表原始 `courseId` | HAR 已确认课表点击详情后的 `getPublishPaperListOfStudent` / `getPaperInCourseInfo` 都以教学班 ID 作为 `courseId` 参数 |

### 1.3 明确不做

- 不做测验答题页
- 不做逐题暂存、正式交卷、结果详情页
- 不补 `getOneCourse` / `getPaperInCourseInfo` 等更重的课程详情主接口
- 不在首页直接内嵌测验列表，入口只放在课程详情页
- 不在 AI课堂里新增独立课表页面；课表接口只用于补齐“我的课程”列表

### 1.4 假设

- FIF token 继续保持 JWT 结构，且 payload 内仍带 `memberId`
- `classId` 可以稳定作为本地导航参数，用于从 `uiState.courses` 中反查课程
- `getPublishPaperListOfStudent` 的已知字段已足够支撑当前 UI，不需要额外补未知字段
- `liveAndRecord/timetableInfo` 返回的是当前学期课表数据；即使结构里有多周排课，最终补数仍只归并到当前学期“我的课程”

## 2. 关键接口

### 2.1 FIF 会话补充 memberId

来源：HAR 中 FIF 首页 `token=...` 的 JWT payload 已包含 `memberId=95f777de234fd428b39d84d6651b8397`。

本次在 `FifSessionManager` 内新增：

```kotlin
suspend fun getMemberUserId(): String?
```

并在 `performSso()` 持久化时一并保存。

### 2.2 测验列表接口

```kotlin
@GET("coursecenter-interaction/paper/getPaperInCourseInfo")
suspend fun getPaperInCourseInfo(
    @Query("courseId") courseId: String,
    @Query("studentId") studentId: String,
    @Header("authorization") auth: String,
    @Header("Visit-Type") visitType: String = "mobile",
): AiClassCoursePaperInfoResponse

@GET("coursecenter-interaction/paper/getPublishPaperListOfStudent")
suspend fun getPublishPaperListOfStudent(
    @Query("courseId") courseId: String,
    @Query("studentId") studentId: String,
    @Query("userId") userId: String,
    @Header("authorization") auth: String,
    @Header("Visit-Type") visitType: String = "mobile",
): AiClassQuizListResponse
```

已确认：

- `getPaperInCourseInfo` 请求参数为 `courseId + studentId`
- `getPublishPaperListOfStudent` 可直接提供历史测验列表与学生视角提交态

UI 本次消费字段：

- `id`
- `title`
- `status`
- `iscommited`
- `publishTime`
- `publishDateTime`
- `publishWeek`
- `answerDuration`

### 2.3 课程补数接口

```kotlin
@GET("coursecenter-interaction/liveAndRecord/timetableInfo")
suspend fun getTimetableInfo(
    @Query("schoolId") schoolId: String,
    @Query("mid") memberId: String,
    @Query("identity") identity: String = "2",
    @Header("authorization") auth: String,
    @Header("Visit-Type") visitType: String = "mobile",
): AiClassTimetableResponse
```

已确认：

- `schoolId` 可由 `getAiktUserIdByMemberId` 响应拿到并持久化
- `mid` 使用 FIF token payload 中的 `memberId`
- 课表条目中的 `className/classNames` 可解析 `(理论)` / `(实验)` 标签
- 课表条目的原始 `courseId` 不能直接用于测验详情；补进课程后，详情页请求应改用 `classId/teachClassId`

### 2.4 ViewModel 详情态

`AiClassUiState` 新增详情页所需状态：

- `selectedCourse: AiCourse?`
- `quizList: List<AiQuiz>`
- `isQuizLoading: Boolean`
- `quizError: String?`

通过 `openCourseDetail(classId)` 负责：

1. 从 `courses` 里找到当前课程
2. 写入 `selectedCourse`
3. 拉取测验列表

### 2.5 详情页展示契约

详情页至少展示：

- 课程名
- 教师名
- 学生人数
- 课程 / 教学班标识（仅已有字段）
- “测验”分区
- 测验条目的标题、发布时间、时长、已提交/未提交状态

空态与错误态：

- 无测验：显示“当前课程暂无测验”
- 拉取失败：显示错误提示 + 重试按钮

## 3. 实现提示

### 3.1 推进顺序

1. **补 FIF 会话中的 memberId 持久化与读取能力**
   - 修改：`core/network/FifSessionManager.kt`
   - 退出信号：Repository 能拿到 `studentId + memberId`

2. **补 AI课堂测验数据模型、API、课程补数与 Repository**
   - 修改：`feature/aiclass/data/AiClassApi.kt`、`feature/aiclass/data/AiClassResponse.kt`、`feature/aiclass/data/AiClassRepository.kt`、`feature/aiclass/domain/AiClassModels.kt`
   - 退出信号：`repository.getQuizList(courseId)` 能稳定返回含历史测验的列表；`repository.getCourses()` 在 `myClassroom` 漏课时能用 `timetableInfo` 补齐当前学期课程，并补出 `理论/实验` 标签

3. **补 ViewModel 详情态与详情页加载动作**
   - 修改：`feature/aiclass/ui/AiClassViewModel.kt`
   - 退出信号：选中课程后能驱动详情页加载测验列表

4. **新增课程详情页与导航入口**
   - 修改：`navigation/Routes.kt`、`navigation/NavGraph.kt`、`feature/aiclass/ui/AiClassHomeScreen.kt`
   - 新建：`feature/aiclass/ui/AiClassCourseDetailScreen.kt`
   - 退出信号：点击课程卡片进入详情页，能看到课程信息与测验区块

5. **架构归并与最小验证**
   - 修改：`easysdd/architecture/aiclass-overview.md`
   - 新建：`app/src/test/...` 相关单测（至少覆盖 token memberId 提取）
   - 退出信号：测试通过，架构文档补到课程详情/测验能力

### 3.2 测试设计

| 功能点 | 验证方式 | 关键用例 |
|---|---|---|
| token 解 memberId | JVM 单测 | JWT payload 含 `memberId` 时可正确提取；缺失时返回 null |
| 课程列表补数 | 手工验证 | `myClassroom` 缺失的当前学期课程能从课表补进“我的课程”；补进课程点击后仍能进入详情页 |
| 测验状态映射 | 代码 review + JVM 单测/函数验证 | `iscommited=true` 显示“已提交”；`false` 显示“未提交” |
| 课程卡片导航 | 手工验证 | 首页点击课程卡片可进入详情页 |
| 详情测验列表 | 手工验证 | 详情页可展示测验分区、空态、错误态 |

### 3.3 高风险实现约束

- `memberId` 提取逻辑必须容错，不能因为 token 结构异常导致 SSO 整体失败
- 课表补数失败不能拖垮首页课程列表；应允许退回 `myClassroom` 单源结果
- 补进课程的详情请求必须使用教学班 ID 作为 `courseId`，否则会出现课程能看见但测验页为空的假阳性
- 测验状态展示必须优先使用 `iscommited`，不能回退成只看 `status`
- 详情页状态应与首页共用同一个 `AiClassViewModel`，避免重复 SSO/重复加载课程列表

### 3.4 要改的文件清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `core/network/FifSessionManager.kt` | 修改 | 持久化/读取 `memberId` |
| `feature/aiclass/data/AiClassApi.kt` | 修改 | 新增测验列表接口 |
| `feature/aiclass/data/AiClassResponse.kt` | 修改 | 新增测验列表与课表响应模型 |
| `feature/aiclass/data/AiClassRepository.kt` | 修改 | 新增 `getQuizList(courseId)`，并把 `timetableInfo` 补数合并进当前学期课程列表 |
| `feature/aiclass/domain/AiClassModels.kt` | 修改 | 新增 `AiQuiz` |
| `feature/aiclass/ui/AiClassViewModel.kt` | 修改 | 新增详情页状态与加载动作 |
| `feature/aiclass/ui/AiClassHomeScreen.kt` | 修改 | 课程卡片点击入口 |
| `feature/aiclass/ui/AiClassCourseDetailScreen.kt` | 新建 | 课程详情与测验列表 UI |
| `navigation/Routes.kt` | 修改 | 新增详情 route |
| `navigation/NavGraph.kt` | 修改 | 注册详情页并共享 ViewModel |
| `easysdd/architecture/aiclass-overview.md` | 修改 | 归并课程详情/测验列表能力 |
| `app/src/test/java/...` | 新建 | 最小单测 |

## 4. 与项目级架构文档的关系

本 feature 改变了 `feature/aiclass/` 的公开能力边界：模块不再只有首页签到与扫码，还新增“课程详情页 + 测验列表读取 + 当前学期课程补数”。因此 acceptance 阶段需要更新 `easysdd/architecture/aiclass-overview.md`：

- `2.1` 三层结构补上 `AiClassCourseDetailScreen`
- `2.4` 从“课程加载”扩成“课程加载 + 课程补数 + 课程详情测验列表”
- `3.1` 数据模型表补上 `AiQuiz`
- `5` 代码锚点补上新页面与新仓储方法

`easysdd/architecture/DESIGN.md` 无需新增入口。
