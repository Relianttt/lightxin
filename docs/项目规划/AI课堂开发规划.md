# AI课堂开发规划

## 功能定位

AI课堂是「爱信校园」中基于 FIF 智慧教学平台的课堂互动模块。核心价值是**课堂签到**（数字码 + 扫码），替代原APP中 WebView + 原生扫码壳的笨重实现。

本模块与 MVP 五大模块（登录/课程表/查寝签到/跑步运动/劳动教育）的关键区别在于：AI课堂走的是**完全独立于校内主站的 FIF 认证体系**，需要单独的 SSO 登录、会话管理和网络层。

---

## 首版范围

### 纳入

| 功能 | 说明 |
|------|------|
| FIF SSO 登录 | 校内 token 换取 FIF 登录态，建立独立会话 |
| 课程列表 | 展示当前学期的 AI课堂课程 |
| 数字码签到 | 输入老师发布的6位数字码完成签到 |
| 扫码签到 | 扫描课堂二维码完成签到 |
| 签到状态 | 查询并展示当前课堂签到状态 |

### 不纳入（后续版本方向）

- 课堂实时状态（正在上课态）
- WebSocket 讨论接入
- 直播 / 录播观看
- 快答 / 试卷 / 评价
- 加入新课堂

---

## 技术方案

### 1. SSO 登录链路

AI课堂的认证不走校内主站 token，而是 `AIIT → FIF` 单点登录：

```
校内 access_token
    ↓
GET aiitpass.fifedu.com/iplat-pass-aiit/h5/login?access_token=...&_userCode=...&...
    ↓ 302
sttp.fifedu.com/studycenter-nh5/?token=...&app=axx
    ↓ 提取 token, 建立 Cookie + SESSION
POST sttp.fifedu.com/studycenter/mobile/common/getAiktUserIdByMemberId
    ↓ 获得 studentId, userName, schoolId
FIF 会话建立完成
```

SSO 入口参数（从校内 TokenManager 获取）：
- `access_token`
- `_userCode` / `userCode`
- `code`（同 userCode）
- `_userName`（URL编码）
- `_userType`
- `appId`
- `returnFromIscToAppFunc=ReturnDefault`

### 2. FIF 会话特征

后续所有 FIF 业务请求需携带：

**Cookie**:
- `id=<studentId>`
- `studentId=<studentId>`
- `currentUserName=<userName>`
- `SESSION=<动态值>`

**请求头**:
- `authorization: Basic <token>`
- `Visit-Type: mobile`
- `Content-Type: application/x-www-form-urlencoded;charset=UTF-8`
- `Referer: https://sttp.fifedu.com/studycenter-nh5/mobileinteraview/havingClass.html`

### 3. 网络层设计

新增两个域名常量和对应 Retrofit 实例：

```
ApiConstants:
  BASE_FIF_SSO = "https://aiitpass.fifedu.com"   # SSO 登录入口
  BASE_FIF     = "https://sttp.fifedu.com"        # FIF 业务接口
```

FIF 需要**独立的 OkHttpClient**（不复用校内 AuthInterceptor）：
- 自带 `CookieJar`（管理 SESSION 等 Cookie）
- 自带 `FifAuthInterceptor`（注入 authorization + Visit-Type + Referer）
- SSO 客户端需禁用自动重定向（`followRedirects(false)`），手动处理 302 提取 token

Hilt 提供方式：
- `@FifSsoRetrofit` — SSO 登录专用，禁止自动重定向
- `@FifRetrofit` — 业务接口，带 CookieJar + 认证头

### 4. 会话管理

新增 `FifSessionManager`，独立于校内 `TokenManager`：

```kotlin
class FifSessionManager {
    // 持久化（DataStore）
    val fifToken: String          // authorization header
    val studentId: String         // FIF 学生 ID (e.g. 3889526)
    val userName: String          // FIF 用户名 (e.g. aiit3233032235)
    val schoolId: String          // FIF 学校 ID
    
    // 运行时
    val cookieJar: CookieJar      // SESSION 等 Cookie
    
    suspend fun performSso()      // 执行 SSO 链路
    fun isSessionValid(): Boolean // 会话是否有效
    fun clear()                   // 清除会话
}
```

---

## 接口清单

### SSO + 会话建立

| 接口 | 方法 | 说明 |
|------|------|------|
| `aiitpass.fifedu.com/iplat-pass-aiit/h5/login` | GET | SSO 跳转，302 获取 FIF token |
| `sttp.fifedu.com/studycenter/mobile/common/getAiktUserIdByMemberId` | POST | 校内身份映射为 FIF 用户 |
| `sttp.fifedu.com/studycenter/mobile/common/termList` | POST | 学期列表（获取 termYear + term） |

### 课程列表

| 接口 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `sttp.fifedu.com/studycenter/mobile/student/myClassroom` | POST | `termYear`, `term` | 课程列表，返回 `data.dataList` |

### 签到

| 接口 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `sttp.fifedu.com/coursecenter-interaction/sign/getSignInCourseInfo` | POST | `teachClassId`, `courseRecordId`, `studentId` | 查询当前签到任务，返回 `signId` |
| `sttp.fifedu.com/coursecenter-interaction/qrcodeV2/checkQrcodeHandler` | POST | `signId`, `userName`, `signCode` | 数字码签到提交 |
| `sttp.fifedu.com/coursecenter-interaction/qrcodeV2/qrcodeHandler` | GET | `token`, `openTheWay=2` | 扫码签到，302 跳转到成功页 |

---

## 标识字典

AI课堂内部存在多套 ID 体系，**不可混用**：

| 字段 | 示例 | 含义 |
|------|------|------|
| `studentId` | `3889526` | FIF 学生 ID，会话核心标识 |
| `currentUserName` | `aiit3233032235` | FIF 用户名 |
| `schoolId` | `2811000226000001678` | FIF 学校 ID |
| `teachClassId` | `3402030000000352381` | 教学班 ID |
| `courseRecordId` | `cc7603debff0465b9492955e789eb687` | 当前上课记录 ID |
| `signId` | `453059` | 签到任务 ID（接口返回，非用户输入） |
| `signCode` | `626922` | 用户输入的6位数字码 |
| `token` | `8af3a7f0...` | 扫码二维码中的 token |

---

## 文件结构

```
feature/aiclass/
├── data/
│   ├── AiClassApi.kt            # FIF 业务接口定义
│   ├── AiClassRepository.kt     # SSO + 课程 + 签到业务封装
│   └── AiClassResponse.kt       # 响应 DTO
├── domain/
│   └── AiClassModels.kt         # AiCourse, SignInInfo 等领域模型
└── ui/
    ├── AiClassHomeScreen.kt     # 课程列表 + 签到入口
    ├── AiClassSignInScreen.kt   # 数字码输入 + 扫码页
    └── AiClassViewModel.kt

core/network/
├── ApiConstants.kt              # +BASE_FIF_SSO, +BASE_FIF
├── NetworkModule.kt             # +@FifSsoRetrofit, +@FifRetrofit
└── FifSessionManager.kt         # FIF 独立会话管理（新建）

navigation/
├── Routes.kt                   # +AICLASS_HOME, +AICLASS_SIGNIN
└── NavGraph.kt                 # +AI课堂路由
```

---

## 页面设计

### AI课堂首页 (AiClassHomeScreen)

```
┌─────────────────────────────┐
│ ←  AI课堂                    │
├─────────────────────────────┤
│                             │
│  ┌─── 快捷签到 ───────────┐ │
│  │  输入数字码    [______] │ │
│  │           [ 签到 ]      │ │
│  └─────────────────────────┘ │
│                             │
│  我的课程                    │
│                             │
│  ┌─────────────────────────┐ │
│  │ 高等数学 B              │ │
│  │ 吉涛 · 42人             │ │
│  └─────────────────────────┘ │
│  ┌─────────────────────────┐ │
│  │ 大学英语 III            │ │
│  │ 张明 · 38人             │ │
│  └─────────────────────────┘ │
│          ...                 │
│                             │
│      [ 扫码签到 ]  (FAB)    │
└─────────────────────────────┘
```

- 顶部快捷签到区：直接输入数字码即可签到，无需选课程
- 课程列表：展示当前学期课程卡片
- 右下角 FAB：打开扫码签到

### 签到结果

签到成功/失败通过 Snackbar 或 Dialog 展示，无需独立页面。

---

## 开发阶段

### Phase 8A: FIF 认证基础设施

**目标**: 打通 SSO 链路，能成功获取 FIF 会话

- `ApiConstants.kt` 新增 `BASE_FIF_SSO` / `BASE_FIF`
- `NetworkModule.kt` 新增 FIF 专用 OkHttpClient（CookieJar + 禁止/允许重定向）+ 两个 Retrofit
- 新建 `FifSessionManager`：实现 SSO 登录 → 用户映射 → 会话持久化
- 新建 `AiClassApi.kt`：定义用户映射和学期列表接口

**验证**: 调用 `myClassroom` 能返回真实课程数据

**关键文件**:
- `core/network/ApiConstants.kt`
- `core/network/NetworkModule.kt`
- `core/network/FifSessionManager.kt` (新建)
- `feature/aiclass/data/AiClassApi.kt` (新建)

### Phase 8B: 课程列表 + 数字码签到

**目标**: 完成课程展示和数字码签到全链路

- `AiClassRepository.kt`：课程列表 + 签到信息查询 + 数字码签到提交
- `AiClassModels.kt`：AiCourse / SignInInfo / SignInResult
- `AiClassResponse.kt`：响应 DTO
- `AiClassHomeScreen.kt`：课程列表 + 快捷数字码签到入口
- `AiClassViewModel.kt`：SSO 触发 + 课程加载 + 签到状态管理
- `Routes.kt` + `NavGraph.kt`：新增 AI课堂路由
- `HomeDashboard.kt`：新增 AI课堂入口卡片
- `ProfileScreen.kt`：新增 AI课堂入口

**验证**: 输入数字码，接口返回 `status: "success"`

### Phase 8C: 扫码签到

**目标**: 集成相机扫码，完成扫码签到链路

- `build.gradle.kts` 添加 ML Kit Barcode Scanning 依赖（或 ZXing）
- `AiClassSignInScreen.kt`（或独立 `AiClassScanScreen.kt`）：相机预览 + 扫码
- 扫码结果解析：提取二维码中的 `token` 参数
- 调用 `qrcodeHandler?token=...&openTheWay=2`，处理 302 响应确认签到成功
- 签到成功 UI 反馈

**验证**: 扫描课堂二维码，完成签到并确认成功

---

## 关键注意事项

1. **SSO 302 处理**: SSO 入口返回 302，需手动提取 `Location` 中的 token，不能让 OkHttp 自动跟随重定向
2. **Cookie 管理**: FIF 会话依赖 Cookie（SESSION），需要 `CookieJar` 持久化，跨请求保持
3. **signId vs signCode**: `signId` 从 `getSignInCourseInfo` 接口获取，`signCode` 是用户输入的6位数字码，两者完全不同
4. **扫码签到不是 JSON 接口**: `qrcodeHandler` 是页面导航 302，需要从 `Location` header 解析结果
5. **字段名 `couseItemId`**: 后端拼写错误但真实如此，实现时必须原样使用
6. **多套 ID 体系**: FIF 内部的 studentId / teachClassId / courseRecordId 等不可混用

---

## 后续版本方向

| 方向 | 说明 |
|------|------|
| 课堂实时状态 | `getWorkingCourseRecordByStudentId` 等接口，展示正在上课信息 |
| WebSocket 讨论 | 接入 FIF 讨论 WebSocket，参与课堂互动 |
| 直播/录播 | 课堂录播列表与播放 |
| 快答/试卷/评价 | 课堂互动活动参与 |

---

**文档版本**: v1.0
**创建日期**: 2026年4月16日
