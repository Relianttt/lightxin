---
doc_type: architecture
slug: aiclass-overview
scope: feature/aiclass/ 的 AI 课堂模块：FIF 独立 SSO 会话、课程列表、当前学期课程补数、课程详情测验列表、数字码签到、ML Kit 扫码签到
summary: aiclass 以独立 OkHttpClient + CookieJar 管理 FIF 平台登录态，SSO 自动续期，支持当前学期课程补数、课程详情测验列表、数字码签到和 ML Kit 扫码签到；扫码签到手动处理 302 跳转
status: current
last_reviewed: 2026-04-22
tags: [aiclass, fif, sso, qrcode, mlkit, barcode]
depends_on: [network-overview]
---

# AI 课堂模块总览

## 0. 术语

- **FIF** — 第三方教学平台 `sttp.fifedu.com`；AI 课堂的课程与签到服务由 FIF 提供，与校内主站 token 体系不互通
- **FIF SSO** — `FifSessionManager` 管理的独立单点登录流程；通过 `performSso()` 自动完成 SSO 握手，获取 FIF 会话 Cookie
- **数字码签到** — 教师公布 6 位数字签到码，学生在 App 中输入后提交
- **扫码签到** — 用 CameraX + ML Kit BarcodeScanning 扫描二维码，解析 token 后调用 FIF 接口签到
- **qrcodeHandler** — FIF 的扫码签到接口，返回 302 跳转（非 JSON），需手动跟随并解析 Location header 判断结果

## 1. 定位与受众

本 doc 描述 `feature/aiclass/` 的职责边界：

- FIF 独立登录态管理（SSO 自动续期）
- 课程列表展示 + 当前学期课程补数 + 课程详情测验列表 + 当前课堂状态查询
- 数字码签到与扫码签到两种方式

适用读者：

- 要改签到流程、加签到方式、或调 SSO 策略的人
- 要理解 FIF 独立会话与校内主站 token 体系如何共存的人
- 要排查"扫码签到总是失败"或"FIF 登录态过期"的人

## 2. 结构与交互

### 2.1 三层结构

```
AiClassApi (data)                 → 7 个 FIF 接口
AiClassRepository (data)          → SSO + 课程 + 测验列表 + 签到 + 扫码（含手动 302 处理）
AiClassViewModel (ui)             → 加载 + 签到触发 + 课程详情测验加载
AiClassHomeScreen (ui)            → 课程列表 + 签到卡片
AiClassCourseDetailScreen (ui)    → 课程基础信息 + 测验列表
AiClassScanScreen (ui)            → CameraX + ML Kit 扫码
```

### 2.2 FIF 独立网络栈

锚点：`core/network/FifSessionManager.kt`、`core/network/FifRetrofit.kt`

AI 课堂使用独立的网络栈与校内主站完全隔离：

- **独立 OkHttpClient**（`@FifOkHttpClient`）— 独立的 CookieJar，不与主站共享
- **独立 Retrofit**（`@FifRetrofit`）— BASE URL 指向 `sttp.fifedu.com`
- **独立 SessionManager**（`FifSessionManager`）— SSO 握手 + Cookie 管理 + auth header 构建
- **额外 header** — 所有 FIF 请求携带 `Visit-Type: mobile`，扫码请求额外携带 WebView 风格的 `User-Agent` 和 `X-Requested-With`

FIF 不使用主站的 `AuthInterceptor`，认证通过 `FifSessionManager.buildAuthHeader()` 手动注入 `authorization` header。

### 2.3 SSO 自动续期

锚点：`feature/aiclass/data/AiClassRepository.kt:40-45`

`ensureSession(forceRefresh)` 是 Repository 的会话保障方法：

1. 检查 `fifSession.isSessionValid()`（基于 Cookie 是否存在）
2. 若无效或强制刷新，调用 `fifSession.performSso()` 执行 SSO 握手
3. 成功后调用 `fifSession.buildAuthHeader()` 构建 auth header

所有公开方法（`getCourses` / `getWorkingRecord` / `getSignInInfo` / `submitSignCode` / `submitQrCode`）在真正请求前都先调 `ensureSession()`。

### 2.4 课程加载 + 课程详情测验列表 + 课堂状态

锚点：`feature/aiclass/data/AiClassRepository.kt:48-106`

`getCourses()` 内部先调 `getTermList()` 获取当前学期（`selected=1`），再调 `getCourses(termYear, term)`。`myClassroom` 返回后，会继续尝试请求 `liveAndRecord/timetableInfo(schoolId, mid, identity=2)`，把课表里存在但“我的课程”缺失的当前学期课程补进结果列表。补数只作为兜底，失败时不会阻断首页课程展示。

课表补数条目会从 `className/classNames` 中解析 `(理论)` / `(实验)` 标签，并把教学班 ID（`classId/teachClassId`）写回详情链路使用的 `courseId`，这是因为 HAR 已确认课表页点击详情后，`getPublishPaperListOfStudent` / `getPaperInCourseInfo` 都以教学班 ID 而不是课表原始 `courseId` 为参数。

`getQuizList(courseId)` 进入课程详情页后会同时利用两类测验数据：以 `paper/getPublishPaperListOfStudent` 作为学生视角主列表，保证历史测验与 `iscommited` 提交态完整；再把 `paper/getPaperInCourseInfo(courseId, studentId)` 解析出的课程详情试卷信息作为补充字段合并进去。前者除 `studentId` 外还需要 `userId`，当前实现从 FIF token 的 JWT payload 中解析 `memberId` 作为 `userId`。

`getWorkingRecord()` 查询当前正在进行的课堂记录，用于首页签到卡片展示。返回 null 表示当前无进行中课堂。

### 2.5 数字码签到流程

锚点：`feature/aiclass/ui/AiClassViewModel.kt:75-106`

1. 从 `workingRecord` 获取 `teachClassId` 和 `courseRecordId`
2. 调 `getSignInInfo(teachClassId, courseRecordId)` 获取 `signId`
3. 若 `hasActiveSign` 为 false，提示"当前没有进行中的签到"
4. 调 `submitSignCode(signId, signCode)` 提交 6 位数字码

UI 层限制输入长度 ≤6，签到按钮在输入达到 6 位且存在进行中课堂时才可点击。

### 2.6 扫码签到流程（手动处理 302 跳转）

锚点：`feature/aiclass/data/AiClassRepository.kt:153-273`

这是模块中最复杂的逻辑。FIF 的扫码签到接口 `qrcodeHandler` 不返回 JSON，而是返回 HTTP 302 重定向。App 需要：

1. 关闭 OkHttp 的自动重定向（`followRedirects(false)`）
2. 从响应 header 读取 `Location`
3. 判断 Location 内容：
   - `signSuccess` → 签到成功
   - `codeExpired` → 二维码已过期
   - `loginManage` → 登录态已失效，强制刷新 SSO 后重试一次
   - 其他 → 未知结果

二维码 token 提取逻辑（`extractQrPayload`）：
- 尝试解析为 URI → 取 `token` 参数
- 回退正则匹配 `token=` 参数
- 回退直接判断是否为 20+ 字符的 base64 字符串（直接作为 token）

### 2.7 扫码页 UI

锚点：`feature/aiclass/ui/AiClassScanScreen.kt:61-416`

`AiClassScanScreen` 使用 CameraX + ML Kit BarcodeScanning：
- 预览用 `PreviewView`，ImplementationMode.COMPATIBLE
- 图像分析用 `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`
- 扫码结果通过 `BarcodeScanner.process()` 异步回调
- ML Kit 的 `ZoomSuggestionOptions` 启用自动变焦
- 支持双指捏合手动变焦，缩放上限与自动变焦一致（`minOf(hardwareMax, 4f)`）
- 成功检测到二维码后显示"扫码成功"过渡态

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `AiCourse` | stableId / id / code / classId / courseId / courseRecordId / courseName / teacherName / studentNum / teachClassId / cover / termYear / term / typeName | `AiClassModels.kt:3-16` |
| `AiQuiz` | id / title / isCommitted / status / publishTime / publishDateTime / publishWeek / answerDurationMinutes | `AiClassModels.kt:28-36` |
| `AiSignInInfo` | signId / teacherName / hasActiveSign | `AiClassModels.kt:14-18` |
| `AiWorkingRecord` | courseRecordId / courseName / courseItemName / teachClassId | `AiClassModels.kt:21-25` |
| `AiClassQrPayload` | rawValue / token | `AiClassQrPayload.kt:3-6` |
| `AiClassUiState` | courses / workingRecord / selectedCourse / quizList / isLoading / isSsoInProgress / isSigningIn / isQuizLoading / error / quizError / signResult | `AiClassViewModel.kt:21-33` |

### 3.2 状态所有权

- `FifSessionManager` 单例持有 FIF 会话状态（Cookie + studentId + userName）
- `AiClassUiState` 由 `AiClassViewModel` 持有
- `selectedCourse + quizList` 同样由 `AiClassViewModel` 持有，课程详情页与首页共享同一个 ViewModel
- `ScanState` 是 `AiClassScanScreen` 的局部 UI 状态（Scanning / Success）

## 4. 关键决策

- **FIF 独立 OkHttpClient + CookieJar** —— 与校内主站 token 体系完全隔离，避免 Cookie 污染。来源：`compound/2026-04-22-decision-fif-independent-okhttp-stack.md`。
- **扫码用 ML Kit 而非 ZXing** —— Google 官方库，维护成本低，自带自动变焦。来源：`compound/2026-04-22-decision-ml-kit-barcode.md`。
- **手动处理 302 跳转** —— FIF 接口返回重定向而非 JSON，App 需要手动跟随。来源：`AiClassRepository.kt:153-273`。
- **SSO 失败后自动重试** —— `submitQrCode` 在检测到 `loginManage` 重定向时，强制刷新 SSO 后重试一次。来源：`AiClassRepository.kt:161-165`。
- **CameraX + PreviewView** —— 使用 Android Jetpack CameraX 库管理相机生命周期，避免直接操作 Camera2 API。

## 5. 代码锚点

- `feature/aiclass/data/AiClassApi.kt:10-79` — 7 个 FIF 接口（含测验列表）
- `feature/aiclass/data/AiClassRepository.kt:34-319` — SSO + 课程 + 测验列表 + 签到 + 扫码全流程
- `feature/aiclass/data/AiClassRepository.kt:153-273` — 扫码签到 302 处理核心
- `feature/aiclass/data/AiClassResponse.kt:6-94` — 响应模型（含测验列表）
- `feature/aiclass/domain/AiClassModels.kt:3-36` — AiCourse / AiQuiz / AiSignInInfo / AiWorkingRecord
- `feature/aiclass/domain/AiClassQrPayload.kt:3-6` — 扫码载荷
- `feature/aiclass/ui/AiClassViewModel.kt:32-191` — 加载 + 签到触发 + 课程详情测验列表
- `feature/aiclass/ui/AiClassHomeScreen.kt:56-316` — 课程列表 + 签到卡片 UI
- `feature/aiclass/ui/AiClassCourseDetailScreen.kt` — 课程基础信息 + 测验列表 UI
- `feature/aiclass/ui/AiClassScanScreen.kt:61-416` — 扫码页 UI + CameraX + ML Kit
- `core/network/FifSessionManager.kt` — FIF SSO + Cookie 管理
- `core/network/FifRetrofit.kt` — 独立 Retrofit 定义

## 6. 已知约束 / 边界情况

- **couseItemId 拼写错误** —— FIF 接口返回的字段名存在拼写错误，代码中保持原样（`courseItemName` 实际为 `@SerializedName("courseItemName")`）。来源：`AiClassResponse.kt:75`。
- **扫码重试只做一次** —— 如果刷新 SSO 后仍然返回 `loginManage`，不会继续重试。来源：`AiClassRepository.kt:161-165`。
- **扫码页不处理权限被永久拒绝** —— 如果用户拒绝相机权限，`CameraPermissionRequest` 调用 `onBack()` 返回。没有引导用户去设置页面开启权限。
- **签到按钮禁用条件** —— `SignInCard` 的签到按钮在 `hasWorkingClass = false` 时禁用，但数字码输入框仍可输入。来源：`AiClassHomeScreen.kt:258`。
- **FIF 会话状态不持久化** —— 进程重启后 CookieJar 丢失，需要重新 SSO。
- **测验列表依赖 token 里的 memberId** —— `paper/getPublishPaperListOfStudent` 需要 `userId`，当前实现通过解析 FIF token 的 JWT payload 取 `memberId`。若后端改 token 结构，需要同步更新 `FifSessionManager.kt`。
- **课程补数依赖 schoolId + memberId** —— `timetableInfo` 同时要求 `schoolId` 和 `mid(memberId)`；任一缺失时当前实现会直接跳过补数，退回 `myClassroom` 单源列表。
- **补进课程使用教学班 ID 进详情** —— 课表条目的原始 `courseId` 不是测验详情使用的那个 ID，若误用会导致课程详情页存在但测验为空。
- **qrcodeHandler 的 URL 构建有两种路径** —— 完整 URL 二维码直接用原值 + 追加参数；纯 token 二维码构造完整 URL。来源：`AiClassRepository.kt:174-181`。

## 7. 相关文档

- 网络层 FIF 独立栈：`easysdd/architecture/network-overview.md`
- FIF 独立 OkHttp 决策：`easysdd/compound/2026-04-22-decision-fif-independent-okhttp-stack.md`
- ML Kit 扫码决策：`easysdd/compound/2026-04-22-decision-ml-kit-barcode.md`
- 架构总入口：`easysdd/architecture/DESIGN.md`
