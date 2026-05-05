---
doc_type: architecture
slug: auth-overview
scope: core/auth/ 的校内认证基础设施：RSA 加密、Token DataStore、登录态/首启态 Flow、TokenRefresher 接口边界
summary: auth 是校内默认栈的身份基础层，TokenManager 以 lightxin_auth DataStore 持久化 token 与用户字段，SessionManager 只暴露登录/首启/退出的窄接口，RSAUtils 同时承载登录密码和跑步数据两套公钥加密，TokenRefresher 作为 network 401 刷新的反向接口。
status: current
last_reviewed: 2026-05-05
tags: [auth, token, datastore, rsa, session, refresh]
depends_on: [login-overview, network-overview]
---

# 认证基础层总览

## 0. 术语

- **校内默认栈身份** — 登录接口返回并写入 `lightxin_auth` DataStore 的 `accessToken / refreshToken / userCode / userName / userType / fileAddress`。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:19-26`。
- **登录态** — `TokenManager.isLoggedIn` 根据 `access_token` 是否非空判断，不额外校验 token 有效期。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:52-54`。
- **首启态** — `TokenManager.isOnboarded` 读取 `lxin_onboarded`；退出登录不会清掉它。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:56-58,111-116`。
- **publicKey / publicKey2** — 登录密码加密公钥与跑步数据加密公钥，两者都在 `RSAUtils` 内，但面向不同服务端协议。锚点：`app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:17-36`。
- **TokenRefresher** — core/auth 定义的刷新接口，由 login 模块实现，network 模块通过它避免直接依赖 login。锚点：`app/src/main/java/com/lightxin/core/auth/TokenRefresher.kt:3-5` / `app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:15-19,78-81`。

## 1. 定位与受众

本 doc 描述 `core/auth/` 的职责边界：

- Token 与用户身份字段的持久化、读取、快照和清除。
- App 起始页所需的登录态 / 首启态 Flow。
- 登录密码与跑步数据上传所需的 RSA 加密工具。
- 401 自动刷新链路所需的 `TokenRefresher` 抽象。

不覆盖：

- 登录页面、登录请求和 refresh token 请求本身；见 `codestable/architecture/login-overview.md`。
- 多域名身份注入、401 拦截器和 FIF 独立会话；见 `codestable/architecture/network-overview.md`。
- 跑步上传请求体结构；见 `codestable/architecture/running-overview.md`。

适用读者：

- 要排查“登录成功但后续接口身份字段缺失”的人。
- 要改退出登录、首启欢迎页、起始页跳转逻辑的人。
- 要确认 `publicKey` 和 `publicKey2` 使用边界的人。

## 2. 结构与交互

### 2.1 四个文件分工

| 文件 | 职责 |
|---|---|
| `RSAUtils.kt` | 两套 RSA 公钥加密：登录密码用 `encryptPassword()`，跑步数据用 `encryptSportData()`。锚点：`app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:15-58` |
| `TokenManager.kt` | `lightxin_auth` DataStore、登录凭据读写、首启态、登录态 Flow、会话清除。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:17-117` |
| `SessionManager.kt` | 面向 UI / navigation 的窄门面：`isLoggedIn / isOnboarded / markOnboarded() / logout()`。锚点：`app/src/main/java/com/lightxin/core/auth/SessionManager.kt:7-21` |
| `TokenRefresher.kt` | 401 自动刷新用接口，隔离 network 和 login 之间的方向依赖。锚点：`app/src/main/java/com/lightxin/core/auth/TokenRefresher.kt:3-5` |

### 2.2 TokenManager 是唯一校内身份持有者

`TokenManager` 使用 `preferencesDataStore(name = "lightxin_auth")` 持久化校内默认栈身份：

- DataStore 声明：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:17`。
- Key 集合：`access_token / refresh_token / user_code / user_name / user_type / file_address / lxin_onboarded`。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:32-40`。
- 登录成功或刷新成功后一次写入 6 个会话字段。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:60-76`。
- 单字段读取方法供 feature 仓库按需读取。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:84-97`。
- `snapshot()` 一次读取全量身份，供 `AuthInterceptor` 在单请求内使用一致字段。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:99-109` / `app/src/main/java/com/lightxin/core/network/AuthInterceptor.kt:36,56,75`。

`TokenManager` 不知道 Retrofit、接口域名或 UI 页面；这些属于 network / feature 层。

### 2.3 SessionManager 是面向导航和 UI 的窄门面

`SessionManager` 只包装 `TokenManager` 的四个会话操作：

- `isLoggedIn` / `isOnboarded` 透传 Flow。锚点：`app/src/main/java/com/lightxin/core/auth/SessionManager.kt:11-12`。
- `markOnboarded()` 委派给 `TokenManager.markOnboarded()`。锚点：`app/src/main/java/com/lightxin/core/auth/SessionManager.kt:14-16`。
- `logout()` 委派给 `TokenManager.clear()`。锚点：`app/src/main/java/com/lightxin/core/auth/SessionManager.kt:18-20`。

当前直接消费者：

- `MainActivity` 注入 `SessionManager` 并传给 `LightXinNavHost`。锚点：`app/src/main/java/com/lightxin/MainActivity.kt:25,62-65`。
- `NavGraph` 合并 `isOnboarded + isLoggedIn` 决定起始页。锚点：`app/src/main/java/com/lightxin/navigation/NavGraph.kt:54-75`。
- `OnboardingScreen` 确认后调用 `markOnboarded()`。锚点：`app/src/main/java/com/lightxin/navigation/NavGraph.kt:100-109`。
- `ProfileViewModel.logout()` 调用 `sessionManager.logout()`。锚点：`app/src/main/java/com/lightxin/feature/home/ui/ProfileViewModel.kt:49-53`。

### 2.4 起始页判定由 onboarded + loggedIn 共同决定

`LightXinNavHost` 将 `SessionManager.isOnboarded` 和 `SessionManager.isLoggedIn` combine 成三态起始页：

1. 未 onboarding → `Routes.ONBOARDING`
2. 已 onboarding 但未登录 → `Routes.LOGIN`
3. 已 onboarding 且已登录 → `Routes.HOME`

锚点：`app/src/main/java/com/lightxin/navigation/NavGraph.kt:59-69`。

因此退出登录只清 token，不清 `lxin_onboarded`，用户会回到登录页而不是欢迎页。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:41-49,111-116`。

### 2.5 401 刷新通过 TokenRefresher 反向接口连接

`core/auth/TokenRefresher` 只有一个方法：`refreshToken(): Boolean`。当前实现者是 `LoginRepository`：

- `LoginRepository : TokenRefresher`。锚点：`app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:15-19`。
- `LoginModule.provideTokenRefresher()` 把 `LoginRepository` 绑定到接口。锚点：`app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:69-81`。
- `TokenRefreshInterceptor` 通过 `dagger.Lazy<TokenRefresher>` 懒注入，401 时调用刷新并重放一次请求。锚点：`app/src/main/java/com/lightxin/core/network/TokenRefreshInterceptor.kt:14-28`。

这个方向让 network 只依赖 core/auth 接口，而不是直接依赖 feature/login；同时用 Lazy 避免网络栈初始化时形成循环依赖。

### 2.6 RSAUtils 同时服务登录与跑步，但公钥不可混用

`RSAUtils` 内有两套公钥：

- `PUBLIC_KEY` → `encryptPassword()`，由 `LoginRepository.login()` 调用。锚点：`app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:17-33` / `app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:20-23`。
- `PUBLIC_KEY_2` → `encryptSportData()`，由 `RunningEncryption` 加密跑步上传字段名和值。锚点：`app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:23-36` / `app/src/main/java/com/lightxin/feature/running/data/RunningEncryption.kt:50-56`。

加密算法统一为 `RSA/None/PKCS1Padding`，按 117 bytes 分段加密后 Base64 NO_WRAP 输出。锚点：`app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:29-58`。

## 3. 数据与状态

### 3.1 AuthCredentials 快照

`AuthCredentials` 是 `TokenManager.snapshot()` 返回的只读身份快照：

| 字段 | 主要用途 |
|---|---|
| `accessToken` | Header/Form 身份注入、FIF SSO token 交换、跑步请求参数 |
| `refreshToken` | `LoginRepository.refreshToken()` 换新 token |
| `userCode` | `_userCode / userCode / xh / studentCode / studentId` 等字段来源 |
| `userName` | 主站 Form `_userName`、FIF SSO query、个人页展示 |
| `userType` | `_userType / userType` 字段来源，缺省常按 `"1"` 处理 |
| `fileAddress` | 登录响应保留字段，当前由 TokenManager 持久化 |

定义锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:19-26`。

### 3.2 SESSION_KEYS 清除边界

退出登录只清以下 6 个会话字段：

- `access_token`
- `refresh_token`
- `user_code`
- `user_name`
- `user_type`
- `file_address`

`KEY_ONBOARDED` 不在 `SESSION_KEYS` 中。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:41-49,111-116`。

这使 `SessionManager.logout()` 的语义是“退出当前校内会话”，不是“重置 App 首次使用状态”。

### 3.3 身份字段的主要消费者

| 消费者 | 读取方式 | 用途 |
|---|---|---|
| `AuthInterceptor` | `snapshot()` | 校内默认栈多域名身份注入。锚点：`app/src/main/java/com/lightxin/core/network/AuthInterceptor.kt:30-105` |
| `LoginRepository` | `getRefreshToken()` + 单字段 fallback | refresh token 成功后补齐缺省字段。锚点：`app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:45-65` |
| `FifSessionManager` | `getAccessToken()/getUserCode()/getUserName()/getUserType()` | FIF SSO query 参数。锚点：`app/src/main/java/com/lightxin/core/network/FifSessionManager.kt:83-86,232-235` |
| `HomeBootstrap` | `getAccessToken()/getUserName()` | 未登录跳过冷启动预加载、首页首帧用户名。锚点：`app/src/main/java/com/lightxin/feature/home/domain/HomeBootstrap.kt:49-53` |
| `ProfileViewModel` | `getUserCode()/getUserName()` | “我的”页展示用户身份。锚点：`app/src/main/java/com/lightxin/feature/home/ui/ProfileViewModel.kt:33-40` |
| feature 仓库 | 单字段读取 | 课表、劳教、跑步、节假日等接口所需业务参数。锚点：`app/src/main/java/com/lightxin/feature/schedule/data/ScheduleRepository.kt:51` / `app/src/main/java/com/lightxin/feature/labor/data/LaborRepository.kt:24-25` / `app/src/main/java/com/lightxin/feature/running/data/RunningRepository.kt:205-208` / `app/src/main/java/com/lightxin/feature/holiday/data/HolidayRepository.kt:165-166` |

## 4. 关键决策

- **Token 与首启态同放 `lightxin_auth`，但退出只清会话字段** —— 简化会话读取入口，同时避免退出登录后重复显示欢迎页。来源：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:17,39-49,111-116`。
- **登录态只看 accessToken 非空** —— 起始页判定不主动发网络校验；token 过期交给请求时的 401 刷新链路处理。来源：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:52-54` / `app/src/main/java/com/lightxin/core/network/TokenRefreshInterceptor.kt:21-28`。
- **AuthInterceptor 使用 snapshot 而不是多次单字段读取** —— 单个请求内身份字段来自同一次 DataStore 快照，避免 token 与 userCode 混用不同读取时刻。来源：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:99-109` / `app/src/main/java/com/lightxin/core/network/AuthInterceptor.kt:36,56,75`。
- **TokenRefresher 放在 core/auth，由 login 实现** —— network 401 刷新链路不直接依赖 feature/login，依赖方向保持 core 接口向外扩展。来源：`app/src/main/java/com/lightxin/core/auth/TokenRefresher.kt:3-5` / `app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:78-81`。
- **publicKey 与 publicKey2 同工具不同用途** —— 登录密码和跑步上传是两套服务端协议，跑步字段名和值都要用 `publicKey2`。来源：`app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:17-36` / `codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md:15-27`。

## 5. 代码锚点

- `app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:15-58` — 双公钥 RSA 分段加密。
- `app/src/main/java/com/lightxin/core/auth/TokenManager.kt:17-26` — DataStore 与 `AuthCredentials`。
- `app/src/main/java/com/lightxin/core/auth/TokenManager.kt:32-49` — 持久化 key 与清除范围。
- `app/src/main/java/com/lightxin/core/auth/TokenManager.kt:52-58` — 登录态 / 首启态 Flow。
- `app/src/main/java/com/lightxin/core/auth/TokenManager.kt:60-76` — 保存登录/刷新后的会话字段。
- `app/src/main/java/com/lightxin/core/auth/TokenManager.kt:84-109` — 单字段读取与快照读取。
- `app/src/main/java/com/lightxin/core/auth/TokenManager.kt:111-116` — 退出登录清除边界。
- `app/src/main/java/com/lightxin/core/auth/SessionManager.kt:7-21` — UI/navigation 会话门面。
- `app/src/main/java/com/lightxin/core/auth/TokenRefresher.kt:3-5` — 401 刷新接口。
- `app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:20-37` — 登录后写 TokenManager。
- `app/src/main/java/com/lightxin/feature/login/data/LoginRepository.kt:45-65` — refresh token 实现。
- `app/src/main/java/com/lightxin/core/network/AuthInterceptor.kt:36,56,75` — snapshot 消费点。
- `app/src/main/java/com/lightxin/core/network/TokenRefreshInterceptor.kt:14-28` — Lazy TokenRefresher + 401 重放。
- `app/src/main/java/com/lightxin/navigation/NavGraph.kt:59-69` — 起始页三态判定。

## 6. 已知约束 / 边界情况

- **`isLoggedIn` 不代表 token 一定有效** —— 只代表本地有非空 accessToken；实际过期由请求返回 401 后刷新。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:52-54` / `app/src/main/java/com/lightxin/core/network/TokenRefreshInterceptor.kt:21-28`。
- **退出登录不清 FIF 会话** —— `SessionManager.logout()` 只到 `TokenManager.clear()`；FIF 独立会话清理属于 `FifSessionManager.clear()`，当前 Profile 退出入口没有联动它。锚点：`app/src/main/java/com/lightxin/core/auth/SessionManager.kt:18-20` / `app/src/main/java/com/lightxin/feature/home/ui/ProfileViewModel.kt:49-53` / `app/src/main/java/com/lightxin/core/network/FifSessionManager.kt:269-270`。
- **`TokenRefreshInterceptor` 重放的是进入该拦截器时的原始 request** —— 默认 OkHttpClient 的拦截器顺序是 `AuthInterceptor → TokenRefreshInterceptor → Logging`；401 后在 `TokenRefreshInterceptor` 内再次 `chain.proceed(chain.request())`，不会重新经过前序 `AuthInterceptor`。锚点：`app/src/main/java/com/lightxin/core/network/NetworkModule.kt:32-50` / `app/src/main/java/com/lightxin/core/network/TokenRefreshInterceptor.kt:25-28`。
- **RSA 公钥硬编码在客户端** —— 这是服务端协议要求，不是可隐藏的客户端秘密。锚点：`app/src/main/java/com/lightxin/core/auth/RSAUtils.kt:17-27`。
- **`fileAddress` 只持久化不在 core/auth 内解释** —— 语义来自登录响应，core/auth 只保存和暴露快照字段。锚点：`app/src/main/java/com/lightxin/core/auth/TokenManager.kt:25,38,66,74,107`。

## 7. 相关文档

- 登录流程与 refresh token 实现：`codestable/architecture/login-overview.md`。
- 多域名身份注入与 401 拦截：`codestable/architecture/network-overview.md`。
- 跑步 publicKey2 加密约束：`codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`。
- 架构总入口：`codestable/architecture/DESIGN.md`
