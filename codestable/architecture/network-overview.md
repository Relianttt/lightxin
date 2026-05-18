---
doc_type: architecture
slug: network-overview
scope: 项目的网络层架构，含多域名 Retrofit 分发、AuthInterceptor 按 host 注入身份、401 自动刷新、以及 AI 课堂 FIF 的独立 SSO 会话
summary: 共享 OkHttpClient + 6 个校内默认栈 Retrofit + 独立 FIF 栈；身份注入由 AuthInterceptor 按 host 四档路由，并通过 TokenManager snapshot 保证单请求内身份字段一致；补充到各 feature 的消费映射与 FIF 扫码直连链路
status: current
last_reviewed: 2026-05-03
tags: [network, auth, fif, retrofit, okhttp]
depends_on: []
---

# 网络层总览

## 0. 术语

- **校内默认栈** — `in.aiit.edu.cn` / `fdygl.aiit.edu.cn` / `sports.aiit.edu.cn:8082` / `ldjy.aiit.edu.cn` 这 4 个域名组成的集合；共用同一个 `OkHttpClient` + `AuthInterceptor` + `TokenRefreshInterceptor`
- **主站域名** — 单指 `in.aiit.edu.cn`。它下挂 `/zhxy-information`、`/zhxy-new-scps`、`/zhxy-csh` 三个子路径，各自对应独立 `@Qualifier` Retrofit 实例（`AuthRetrofit` / `MainRetrofit` / `CshRetrofit`）
- **FIF** — AI 课堂接入的第三方教学平台（`aiitpass.fifedu.com` SSO + `sttp.fifedu.com` 业务）。独立 `OkHttpClient`、独立 `CookieJar`、独立身份字段；与校内默认栈 token 体系不互通
- **身份参数** — `access_token / userCode / userName / userType / appId` 这组由校内登录接口返回的字段；按目标域名不同，注入方式有 Form 表单追加、Header 注入、Query 追加三种

## 1. 定位与受众

本 doc 覆盖项目里"**请求发出去之前**"的全部基础设施：Hilt 注入的 Retrofit 实例、`OkHttpClient` 装配、拦截器链、身份信息读取来源、FIF 单点登录与会话建立。

读者：

- 新增一个服务端域名时（`feature-design` 阶段）—— 决定用哪个 `@Qualifier`、要不要扩 `AuthInterceptor`
- 查寝 / 跑步 / AI 课堂等模块出现 "401 / 非法访问 / 身份失效" 类问题时（`issue-analyze` 阶段）—— 定位身份注入链路
- 新人理解"为什么 FIF 要独立一套"时

读完能做到：知道请求身份从哪读、在哪注入、401 怎么自动续、FIF SSO 怎么握手。

## 2. 结构与交互

### 2.1 两条独立的 OkHttp 栈

项目里有**两个** `OkHttpClient` 单例，**不共享拦截器、不共享 CookieJar**：

| 客户端 | 拦截器 | CookieJar | 使用者 |
|---|---|---|---|
| 默认（`provideOkHttpClient`） | `AuthInterceptor` → `TokenRefreshInterceptor` → `HttpLoggingInterceptor` | 无 | 校内默认栈 4 个域名，共 6 个 Retrofit |
| `@FifOkHttpClient`（`provideFifOkHttpClient`） | 只有 Logging | 内存 CookieJar（`ConcurrentHashMap<host, List<Cookie>>`） | AI 课堂 |

锚点：`core/network/NetworkModule.kt:32-50`（默认 client）/ `core/network/NetworkModule.kt:99-110`（FIF client）。

两条栈不共享的原因：
- FIF 身份是 `authorization: Basic <token>` + `SESSION` Cookie，和校内默认栈的 `access_token` 字段体系不互通；若共用 `AuthInterceptor`，会把校内参数错误注入到 FIF 请求里（§2.4 第 4 档默认行为）
- FIF SSO 必须手动处理 302 Location 来提取 token，需要在调用时 `followRedirects(false)` 局部覆盖（`core/network/FifSessionManager.kt:129-133`）

### 2.2 10 个 `@Qualifier`（9 Retrofit + 1 OkHttpClient）

```
AuthRetrofit       → in.aiit.edu.cn/zhxy-information  (登录 + token 刷新)
MainRetrofit       → in.aiit.edu.cn/zhxy-new-scps     (首页 / 消息 / 服务)
CshRetrofit        → in.aiit.edu.cn/zhxy-csh          (课表 + 考试成绩)
CheckinRetrofit    → fdygl.aiit.edu.cn                (查寝)
SportsRetrofit     → sports.aiit.edu.cn:8082          (跑步)
LaborRetrofit      → ldjy.aiit.edu.cn                 (劳教)
CreditRetrofit     → cqc.aiit.edu.cn                  (素质学分)
FifRetrofit        → sttp.fifedu.com                  (AI 课堂)
IzuoyeRetrofit     → izuoye.fifedu.com                (爱作业平台)
FifOkHttpClient    → 区分 FIF 与默认两个 OkHttpClient 实例
```

前 7 个 Retrofit（Auth ~ Credit）共享默认 `OkHttpClient`；`FifRetrofit` 和 `IzuoyeRetrofit` 注入 `@FifOkHttpClient` 的独立 client。锚点：`core/network/NetworkModule.kt`。

CreditRetrofit 复用主 OkHttpClient，AuthInterceptor 默认分支会注入多余参数到 FormBody，服务端忽略。IzuoyeRetrofit 复用 FifOkHttpClient，认证通过各接口方法显式传入 `@Header("jtzy")`。

为什么主站域名拆三个 Retrofit？各子系统 baseUrl 有不同路径前缀（`/zhxy-information` / `/zhxy-new-scps` / `/zhxy-csh`），Retrofit 的 `@POST("xxx")` 是相对 baseUrl 的，拆成三个让 Api 接口的相对路径更短。

### 2.3 当前消费映射：从 `@Qualifier` 到 feature

当前代码里，网络 provider 和调用方的对应关系如下：

| Provider | 当前消费者 | 说明 |
|---|---|---|
| `@AuthRetrofit` | `LoginRepository` | 只承载登录与 refresh token（`mobile/getToken.do` / `mobile/refresh.do`）；同时作为 `TokenRefresher` 实现供 401 重试链路调用 |
| `@MainRetrofit` | 暂无直接消费者 | provider 已注册，但当前 feature 尚未注入使用 |
| `@CshRetrofit` | `ScheduleRepository` | 课表与周次 |
| `@CheckinRetrofit` | `CheckinRepository` / `FileUploadApi` / `HolidayApi` | 同一 host 下同时承载 JSON 业务接口、Multipart 文件上传、节假日登记接口 |
| `@SportsRetrofit` | `RunningApi` | 跑步业务接口 |
| `@LaborRetrofit` | `LaborRepository` | 劳教只读查询接口 |
| `@FifRetrofit` | `AiClassApi` | AI 课堂课程、签到、课堂状态 |
| `@FifOkHttpClient` | `FifSessionManager` / `AiClassRepository` | 前者做 SSO，会话建立；后者做扫码签到的原始 OkHttp 请求 |

锚点：`feature/login/data/LoginRepository.kt:16-19,78-79` / `feature/schedule/data/ScheduleRepository.kt:108-109` / `feature/checkin/data/CheckinRepository.kt:189-195` / `feature/holiday/data/HolidayRepository.kt:204-209` / `feature/running/data/RunningApi.kt:52-55` / `feature/labor/data/LaborRepository.kt:118-121` / `feature/aiclass/data/AiClassRepository.kt:33-38,305-306`。

这张映射表回答两个实现问题：

- 新接口该复用哪一个 provider，而不是再建一个 Retrofit
- 看见调用失败时，先沿着"feature → provider → client → interceptor"这条链回溯

### 2.4 `AuthInterceptor`：按 host 四档分发

身份参数注入不是"所有请求一视同仁" —— 不同服务端对同一套身份的承载方式不同。`AuthInterceptor.intercept()` 按以下优先级判定（`core/network/AuthInterceptor.kt:30-105`）：

| 档 | 匹配条件 | 注入方式 |
|---|---|---|
| 1 | 路径含 `getToken.do` / `refresh.do` | 跳过（登录本身不需要身份） |
| 2 | host 含 `sports.aiit.edu.cn` | Header 注入 10 个字段（含 `studentCode` / `Origin` / `Referer`） |
| 3 | host 含 `fdygl.aiit.edu.cn` | Header 注入 9 个字段（含 `Authorization: Bearer` / `X-Requested-With` / `Origin` / `Referer`） |
| 4 | 默认（主站域名 `in.aiit.edu.cn` + `ldjy.aiit.edu.cn`） | FormBody 追加 6 个字段；劳教再追加 4 个 |

**分 4 档的判定依据**：**服务端对身份的承载方式**（跳过 / Header 多字段 / Header + Bearer / Form 追加）。新增域名时，先看其 H5 侧抓包的身份在哪（Header、Cookie、Form、Query）、服务端要求哪几个字段，再决定归档或开新档。

第 4 档只对 `FormBody` 生效（`core/network/AuthInterceptor.kt:80`）—— Retrofit 用 `@FormUrlEncoded + @POST` 时 body 才是 FormBody，GET 或 JSON body 不会被注入。这是隐式约束：**主站域名的业务接口必须用 Form 表单**（见 §6 第 1 条）。

### 2.5 `TokenRefreshInterceptor`：401 自动刷新

拦截器链的第二道闸（`core/network/NetworkModule.kt:43-44` 保证顺序：Auth 先、Refresh 后）。

- 触发：`response.code == 401`（`core/network/TokenRefreshInterceptor.kt:21`）
- 处理：通过 `dagger.Lazy<TokenRefresher>` 懒注入（`core/network/TokenRefreshInterceptor.kt:15`），调用 core 接口 `refreshToken()`；当前实现由 `LoginRepository` 提供（`feature/login/data/LoginRepository.kt:16-19,78-79`）
- 重放：刷新成功重放一次（`core/network/TokenRefreshInterceptor.kt:25-28`），只重放一次 —— 二次 401 不再重试

**不在 FIF 栈里**：FIF token 失效由 `FifSessionManager.isSessionValid()` 在业务调用前判定，失效时重新 `performSso()`；FIF `OkHttpClient` 本身就没装 `TokenRefreshInterceptor`（`core/network/NetworkModule.kt:99-110`）。

### 2.6 FIF SSO 三步握手

`FifSessionManager.performSso()`（`core/network/FifSessionManager.kt:74-108`）做一次完整登录。**三步分别对应**：**token 交换** → **会话建立** → **身份映射**；任一步失败都不进入下一步、不落盘。

1. **手动 302 提取 token**（token 交换，`core/network/FifSessionManager.kt:109-149`）
   - 构造 `aiitpass.fifedu.com/iplat-pass-aiit/h5/login?access_token=...&_userCode=...&...`（8 个 query 参数，`FifSessionManager.kt:119-126`）
   - 用 `fifClient.newBuilder().followRedirects(false).build()` 局部禁用自动重定向
   - 从 302 `Location` 的 `?token=xxx` 提取（优先 `HttpUrl.queryParameter`，失败再用正则兜底，`FifSessionManager.kt:143-148`）

2. **建立 SESSION Cookie**（会话建立，`core/network/FifSessionManager.kt:154-158`）
   - GET `sttp.fifedu.com/studycenter-nh5/?token=xxx&app=axx`
   - 响应被 `CookieJar.saveFromResponse` 自动落到内存 map
   - 不解析响应 body，只为触发 Cookie 存储

3. **用户映射**（身份映射，`core/network/FifSessionManager.kt:163-188`）
   - POST `sttp.fifedu.com/studycenter/mobile/common/getAiktUserIdByMemberId`
   - Header: `authorization: Basic <token>` + `Visit-Type: mobile`
   - 从 `data.id / data.userName / data.schoolId` 取出 FIF 身份

三步全过才持久化到独立 DataStore `fif_session`（`core/network/FifSessionManager.kt:97-102`）。

### 2.7 FIF 扫码签到：绕过 Retrofit 的直连支线

AI 课堂大多数业务请求走 `AiClassApi + @FifRetrofit`，但**扫码签到不是 Retrofit 接口**。`AiClassRepository.submitQrCode()` 直接拼原始 URL，并用 `@FifOkHttpClient` 发一个禁止重定向的 GET（`feature/aiclass/data/AiClassRepository.kt:153-273`）。

链路拆成 4 步：

1. **确保会话**：`ensureSession()` 先做 `isSessionValid()` 检查，必要时强制 `performSso()`，然后取 `authorization` header（`AiClassRepository.kt:39-45`）
2. **补登录 query**：`buildQrHandlerUrl()` 把扫码结果归一成 `qrcodeHandler` URL，再通过 `FifSessionManager.appendQrLoginParams()` 补齐 `access_token / _userCode / userCode / _userName / _userType / appId / returnFromIscToAppFunc`（`AiClassRepository.kt:174-182` / `core/network/FifSessionManager.kt:198-234`）
3. **合并 Cookie**：`buildQrCookieHeader()` 读取 `CookieJar` 里已建立的 cookie，再人工补 `id / studentId / currentUserName` 三个 cookie 名（`AiClassRepository.kt:184-202`）
4. **手动处理 302**：`executeQrCodeRequest()` 用 `fifClient.newBuilder().followRedirects(false)` 发送 GET，请求里带 `authorization / Visit-Type / X-Requested-With / Cookie / WebView 风格 User-Agent`；返回后按 `Location` 判断是成功、二维码过期，还是跳回登录页（`AiClassRepository.kt:229-273`）

如果第一次 302 跳到了 `loginManage`，仓库会强制重跑一次 SSO 再重试一遍扫码请求（`AiClassRepository.kt:159-167,214-215`）。这条支线和 §2.6 共用同一个 FIF 会话，但**不是** `AiClassApi` 的一部分，排查扫码问题时必须把它当作一条单独网络链路来看。

## 3. 数据与状态

### 3.1 身份数据的两套 DataStore

| DataStore 名 | 持有类 | 字段 |
|---|---|---|
| `lightxin_auth` | `TokenManager` | `access_token / refresh_token / user_code / user_name / user_type / file_address / lxin_onboarded` |
| `fif_session` | `FifSessionManager` | `fif_token / student_id / user_name / school_id` |

锚点：`core/auth/TokenManager.kt:17` / `core/network/FifSessionManager.kt:23`。

两套分开的原因同 §2.1：身份体系不互通；同时 `user_name` 在两边的含义不同（校内是真实姓名，FIF 是 `aiit{学号}` 格式），不能共用字段。

校内默认栈读取身份时，`AuthInterceptor` 不再逐字段调用 `TokenManager.getAccessToken()` / `getUserCode()` 等方法，而是每个 host 分支调用一次 `TokenManager.snapshot()`，得到同一批 `AuthCredentials` 后再按目标域名映射到 header 或 Form 字段。这样单个请求内不会出现 accessToken 与 userCode 来自不同 DataStore 时刻的混合状态，同时减少重复 `DataStore.data.first()` 读取。锚点：`core/auth/TokenManager.kt:19-26,99-111` / `core/network/AuthInterceptor.kt:36,56,75`。

### 3.2 会话有效性判定

- 校内默认栈：`TokenManager.isLoggedIn`（Flow，`core/auth/TokenManager.kt:43-45`）—— 仅看 `access_token` 非空
- FIF：`FifSessionManager.isSessionValid()`（`core/network/FifSessionManager.kt:55-58`）—— **三项都有效**：`studentId` + `fif_token` + `SESSION` Cookie（Cookie 从 CookieJar 反查，`FifSessionManager.kt:60-65`）

FIF 多一层 Cookie 检查的原因是 token 本身不含过期时间，但 `SESSION` Cookie 由服务端管理；三项齐全才能直接发业务请求，否则走 `performSso()`。

### 3.3 退出登录的清除边界

业务层退出统一走 `core/auth/SessionManager.logout()`（`SessionManager.kt:18-20`），内部委派给 `TokenManager.clear()`；FIF 侧需另行调 `FifSessionManager.clear()`。

- `TokenManager.clear()`（`core/auth/TokenManager.kt:91-95`）—— 只清 `SESSION_KEYS`（6 个），**保留 `lxin_onboarded`**，避免退出登录后重现欢迎页
- `FifSessionManager.clear()`（`core/network/FifSessionManager.kt:237-242`）—— 清 DataStore；Cookie 不主动清（代码注释说"无法直接清除，但会话失效后不影响" —— CookieJar 是内存实例，App 重启即丢；另见 §6 观察项）

当前 UI 退出入口 `ProfileViewModel.logout()` 只调用了 `sessionManager.logout()`，并没有联动 `FifSessionManager.clear()`（`feature/home/ui/ProfileViewModel.kt:49-53`）。这意味着**校内主站退出**和**FIF 会话清理**在代码上是两条独立路径。

## 4. 关键决策

本节只列**有明确主动来源**的决策。另有两条"从代码现状反推出的硬约束"未正式归档（Form 表单 / 查寝多头鉴权），暂放 §6 作为约束对待，是否升级为正式决策待用户评估。

- **FIF 必须独立 OkHttp 栈**（§2.1）—— 来源 `docs/项目规划/AI课堂开发规划.md` §技术方案"独立的 OkHttpClient"。引用：`codestable/compound/2026-04-22-decision-fif-independent-okhttp-stack.md`。

## 5. 代码锚点

- `core/network/NetworkModule.kt` — 所有 Hilt provider + 8 个 `@Qualifier` 声明
- `core/network/ApiConstants.kt` — 9 个域名常量（含 FIF SSO / FIF）+ `APP_ID`
- `core/network/AuthInterceptor.kt:30-105` — 按 host 四档路由
- `core/network/TokenRefreshInterceptor.kt:21-29` — 401 重试逻辑
- `core/auth/TokenRefresher.kt:3-5` — token 刷新抽象，避免 `core/network` 直接依赖 login feature
- `core/network/FifSessionManager.kt:74-108` — SSO 三步主入口
- `core/network/FifSessionManager.kt:109-149` — 手动 302 提取 token
- `core/network/FifSessionManager.kt:198-234` — 为扫码链路补齐 FIF 登录 query
- `feature/aiclass/data/AiClassRepository.kt:39-45` — `ensureSession()`，连接 FIF 会话与业务请求
- `feature/aiclass/data/AiClassRepository.kt:153-273` — 扫码签到直连链路
- `feature/home/ui/ProfileViewModel.kt:49-53` — 当前 UI 退出入口
- `core/auth/SessionManager.kt:18-20` — 业务层退出入口
- `core/auth/TokenManager.kt:19-26` — 校内认证字段快照模型 `AuthCredentials`
- `core/auth/TokenManager.kt:33-40` — 退出登录清除边界（`SESSION_KEYS` 常量）
- `core/auth/TokenManager.kt:99-111` — `snapshot()` 一次读取当前校内认证字段
- `core/auth/TokenManager.kt:17` — 校内 DataStore 名
- `core/network/FifSessionManager.kt:23` — FIF DataStore 名

## 6. 已知约束 / 边界情况

面向实施者（调用方 / feature-design / issue-analyze）的约束清单：

- **主站域名的业务接口必须用 Form 表单** —— `AuthInterceptor` 默认档只注入到 `FormBody`，GET / JSON 请求不会被注入身份参数（`AuthInterceptor.kt:80`）。来源：AuthInterceptor 实现反推约束；未正式归档。
- **查寝接口鉴权不是单一 Bearer token** —— 必须同时带 `accessToken / access_token / userCode / xh / userType / X-Requested-With / Origin / Referer`，否则返回 `-100 非法访问`（`AuthInterceptor.kt:54-69` 实际注入的 9 个字段；顶部注释与此不符，见最末观察项）。来源：`docs/接口分析/查寝接口适配记录.md` §1 踩坑实录。
- **FIF SSO 禁止自动重定向** —— 必须 `followRedirects(false)` 才能从 302 Location 提取 token（`FifSessionManager.kt:129-133`）。原因：token 仅在 Location header 里出现一次，跟随跳转后就丢失。
- **FIF 扫码签到不是 Retrofit 调用** —— 必须保留 `authorization + Visit-Type + Cookie + WebView 风格头 + 禁止重定向` 这一整套请求形态，不能简单改成 Retrofit `@GET`（`feature/aiclass/data/AiClassRepository.kt:229-273`）。
- **劳教链路是"调用方显式传三字段 + Interceptor 再补字段"的双轨** —— `LaborRepository` 先显式传 `userCode / xh / accessToken`（`feature/labor/data/LaborRepository.kt:23-35` / `feature/labor/data/LaborApi.kt:9-35`），`AuthInterceptor` 劳教分支再追加 `_userCode / _userName / _userType / appId` 以及一组劳教额外字段（`core/network/AuthInterceptor.kt:85-97`）。改这条链路时，两边要一起看。
- **跑步数据加密必须字段名和值都加密**（业务层约束，使用 `SportsRetrofit` 上游）：用 `RSAUtils.encryptSportData()`（`core/auth/RSAUtils.kt:36`）。来源：`docs/接口分析/跑步接口深度分析报告.md`。
- **观察项：`AuthInterceptor` 顶部注释过时**（`AuthInterceptor.kt:16`）—— 注释说"查寝不由 Interceptor 注入（在 Api 层处理）"，但实际 54-69 行已经注入。交给下次维护者修注释。
- **观察项：`MainRetrofit` 当前无消费者**（`core/network/NetworkModule.kt:64-66`）—— provider 已存在，但 `app/src/main/java/com/lightxin/feature/` 下暂无注入点；后续若新增主站 `zhxy-new-scps` 请求，应先复用这条 provider。
- **观察项：当前退出链不会清 FIF 会话**（`feature/home/ui/ProfileViewModel.kt:49-53` / `core/auth/SessionManager.kt:18-20`）—— UI 登出只清校内 DataStore，不会触发 `FifSessionManager.clear()`。
- **观察项：`FifSessionManager.clear()` 中 `cookieJar.loadForRequest(fifUrl)` 是无效调用**（`FifSessionManager.kt:241`）—— 返回列表后什么都不做，没清 Cookie 也没用返回值；应改为自定义 `CookieJar` 暴露 `clear(host)`，或依赖 App 重启。交给下次维护者修。

## 7. 相关文档

- 架构总入口：`codestable/architecture/DESIGN.md`
- 接口协议参考：`docs/接口分析/APK逆向分析报告.md`、`docs/接口分析/AI课堂接口分析报告.md`
- 踩坑实录：`docs/接口分析/查寝接口适配记录.md`、`docs/接口分析/跑步接口深度分析报告.md`
- 历史规划（多数已实现）：`docs/项目规划/项目规划.md` §多域名网络层、`docs/项目规划/AI课堂开发规划.md` §技术方案

## 变更日志

- 2026-05-03：同步 401 自动刷新链路的依赖方向：`TokenRefreshInterceptor` 改依赖 `TokenRefresher` 接口；`HolidayApi` 改由 `@CheckinRetrofit` provider 注入。
- 2026-05-03：同步 `TokenManager.snapshot()` 认证快照现状：`AuthInterceptor` 每个 host 分支一次读取认证字段，替代逐字段重复读取。
- 2026-04-21：补齐 `@Qualifier` 到 feature 的消费映射、FIF 扫码签到直连链路，以及退出登录与 FIF 会话解耦等当前边界。
