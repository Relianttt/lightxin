---
doc_type: architecture
slug: login-overview
scope: feature/login/ 的登录流程、RSA 密码加密、Token 持久化，以及与其他模块的认证共享边界
summary: login 以 LoginRepository 封装 RSA 加密与 Token 存取，UI 层是单表单无状态机，认证凭据写入 TokenManager 后供 AuthInterceptor 消费
status: current
last_reviewed: 2026-04-22
tags: [login, auth, rsa, token, datastore]
depends_on: [network-overview]
---

# 登录模块总览

## 0. 术语

- **RSA 密码加密** — `RSAUtils.encryptPassword()` 用 `publicKey` 对明文密码加密后再传输；与跑步模块的 `publicKey2` 是两组不同的密钥
- **Token 体系** — 登录成功后返回 `accessToken + refreshToken`，经 `TokenManager.saveLoginData()` 持久化到 DataStore，供后续请求的 `AuthInterceptor` 取用
- **登录态判定** — `SessionManager.isLoggedIn` 基于 `TokenManager` 中的 accessToken 是否存在且非空

## 1. 定位与受众

本 doc 描述 `feature/login/` 的职责边界：

- 登录请求的入口，不是全局认证中心（认证由 `core/auth/` 的 `TokenManager` + `AuthInterceptor` 承载）
- 只处理首次登录与 token 刷新两种场景；退出登录由 `HomeScreen` 或 `SessionManager.clearSession()` 触发，不在 login 模块范围内

适用读者：

- 要改登录表单流程、加验证方式、或调 RSA 加密策略的人
- 要理解 accessToken / refreshToken 在项目里的存储与流转的人
- 要排查"登录成功了但后续请求仍然 401"的人

## 2. 结构与交互

### 2.1 三层结构：Api → Repository → ViewModel

login 是项目中最简洁的 feature 模块，三个文件对应 clean architecture 三层：

```
LoginApi (data)       → POST mobile/getToken.do
                       POST mobile/refresh.do
LoginRepository (data) → 加密 → 调用 → 落 Token
LoginViewModel (ui)   → 输入状态 + 触发
LoginScreen (ui)      → 表单渲染
```

没有 domain 层仓储（`LoginModels.kt` 只定义了 `UserInfo`，但当前未被 ViewModel 使用）。

### 2.2 LoginRepository 承担加密 + 网络 + Token 持久化三件事

锚点：`feature/login/data/LoginRepository.kt:19-63`

`login()` 的执行顺序：

1. `RSAUtils.encryptPassword(password)` 加密明文密码
2. 调用 `LoginApi.login(userCode, encryptedPassword)`
3. 从响应中取出 `TokenInfo`（`accessToken / refreshToken / userCode / userName / userType / fileAddress`）
4. 全部写入 `TokenManager.saveLoginData()`

`refreshToken()` 同理，用本地保存的 refreshToken 换取新 token 对。

### 2.3 LoginViewModel 管理表单输入与提交状态

锚点：`feature/login/ui/LoginViewModel.kt:30-85`

`LoginUiState` 包含：
- `userCode` / `password` — 双向绑定
- `isLoading` — 防重复提交
- `error` — 错误提示
- `loginSuccess` — 驱动导航跳转

ViewModel init 时从 DataStore 读取上次保存的学号（`loadSavedUserCode()`），方便用户快速重新登录。

登录成功时：
1. 保存学号到 `lightxin_login` DataStore
2. 置 `loginSuccess = true`
3. 外部 `LoginScreen` 的 `LaunchedEffect` 监听此信号触发导航

### 2.4 LoginScreen 是单页表单，无多步骤流程

锚点：`feature/login/ui/LoginScreen.kt:57-192`

UI 结构：
- 顶部插画框（`LoginIllustration` — 由 Canvas 绘制，不在本模块内）
- 表单卡：学号输入框 + 密码输入框（带显隐切换）+ 错误提示 + 登录按钮/加载指示器

输入框使用 `LxTextField`（designsystem 组件），错误提示用 `AnimatedVisibility` 做渐入渐出。整体布局包含 `imePadding()` 处理键盘弹起。

## 3. 数据与状态

### 3.1 关键状态模型

| 类型 | 含义 | 定义位置 |
|---|---|---|
| `LoginUiState` | 表单输入 + 加载 + 错误 + 成功信号 | `LoginViewModel.kt:22-28` |
| `TokenInfo` | accessToken / refreshToken / userCode / userName / userType / fileAddress | `LoginResponse.kt:15-22` |
| `UserInfo` | userCode / userName / userType / fileAddress（领域模型，当前未使用） | `LoginModels.kt:3-8` |

### 3.2 状态所有权

- `LoginUiState` 由 `LoginViewModel` 持有，页面级状态
- Token 持久化由 `TokenManager`（DataStore）持有，跨 ViewModel / 跨进程共享
- 学号缓存由独立的 `lightxin_login` DataStore 持有（与 Token DataStore 分离）

### 3.3 Token 刷新不在 login 模块内

token 过期后的自动刷新由 `core/network/TokenRefreshInterceptor` 拦截 401 后触发，调用 `LoginRepository.refreshToken()`。这意味着 `LoginRepository` 被两个调用方共享：
- `LoginViewModel` — 首次登录
- `TokenRefreshInterceptor` — 静默刷新

## 4. 关键决策

- **密码在客户端 RSA 加密后传输** —— 不使用 HTTPS 传输明文密码；`publicKey` 硬编码在客户端。来源：`feature/login/data/LoginRepository.kt:21`、`core/auth/RSAUtils.kt`。这条直接受后端接口约束。
- **Token 写入 DataStore 而非内存或 SharedPreferences** —— 与项目的偏好存储策略一致。来源：`core/auth/TokenManager.kt`。
- **登录成功后跳转使用 `popUpTo(LOGIN) { inclusive = true }`** —— 防止用户按返回键回到登录页。来源：`NavGraph.kt:124-126`。
- **学号缓存独立于 Token DataStore** —— 即使 token 过期，学号仍然保留，方便快速重新登录。来源：`LoginViewModel.kt:19,44-47`。

## 5. 代码锚点

- `feature/login/data/LoginApi.kt:9-21` — 登录与刷新两个接口
- `feature/login/data/LoginRepository.kt:19-63` — 加密 → 调用 → 落 Token 主链
- `feature/login/data/LoginResponse.kt:6-22` — 响应模型与 TokenInfo
- `feature/login/ui/LoginViewModel.kt:30-85` — 表单状态管理与登录触发
- `feature/login/ui/LoginScreen.kt:57-192` — 表单 UI 渲染
- `core/auth/TokenManager.kt` — Token 读写接口
- `core/auth/RSAUtils.kt` — RSA 加密工具
- `core/network/TokenRefreshInterceptor.kt` — 401 自动刷新

## 6. 已知约束 / 边界情况

- **UserInfo 领域模型未被使用** —— `LoginModels.kt:3-8` 定义了 `UserInfo`，但 ViewModel 直接消费 `TokenInfo`，没有中间映射层。未来若需要跨模块传递用户信息，需决定是否启用此模型。
- **login 不负责退出登录** —— 退出由 `SessionManager.clearSession()` 处理，入口在 `HomeScreen` 的"我的"页。来源：`feature/home/ui/HomeScreen.kt`。
- **登录成功导航后 LoginViewModel 不会被立即销毁** —— 因为使用了 `popUpTo(LOGIN) { inclusive = true }`，LoginScreen 会从返回栈移除，ViewModel 也会被清理。
- **学号缓存与 Token DataStore 分离的设计意味着"清除 Token 不影响学号"** —— 这是有意的。

## 7. 相关文档

- 网络层多域名架构：`easysdd/architecture/network-overview.md`
- RSA 双密钥决策：`easysdd/compound/2026-04-22-decision-running-dual-rsa-encryption.md`
- 架构总入口：`easysdd/architecture/DESIGN.md`
