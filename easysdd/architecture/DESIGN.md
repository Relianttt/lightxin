# 轻小信 (LightXin) 架构总入口

> 状态：已补齐（2026-04-22 完成全部 12 份模块级架构文档）
> 创建日期：2026-04-21（最近更新：2026-04-22）

## 1. 项目简介

轻小信是基于「爱信校园」(cn.edu.aiit.axx) 接口协议开发的轻量 Android 原生客户端，使用 Kotlin + Jetpack Compose 实现。目标是替代原 H5 + WebView 壳的笨重体验，提供精致、克制、Anthropic / iOS 18 风格的校园工具 UI。

**当前实现状态**（截至 2026-04-22，共 9 个 feature 包、18 条路由）：

- MVP 五大模块（登录 / 课程表 / 查寝 / 跑步 / 劳教）已全部上线
- 在 MVP 之上还叠加：AI 课堂（FIF 独立会话 + 数字码 / 扫码签到）、首启欢迎页、关于页 + 调试开关、首页"此刻我该做什么"叙事场景（7 类场景 + 副标题动态轮换 + 启动预加载）、跑步路线模板系统（录制 / 列表 / 详情 / 质量校验 / 模板驱动模拟）

`docs/` 下多份规划文档是这些阶段当时的蓝本，多数已是"完成记录"而非未来方向；接口协议文档（`docs/接口分析/`）仍是长期有效的参考与踩坑实录。

## 2. 核心概念 / 术语表

| 术语 | 含义 |
|---|---|
| `Design System` | `core/designsystem/` 下的 Token + 通用组件。暖色陶土 / 羊皮纸质感 / Newsreader + Outfit + Noto Serif SC 三字族；权威定义与 Phase 9A/9B/9C 的决策沉淀在 `docs/项目规划/UI改造指南.md`（多数已落地） |
| `HomeScene` | 首页叙事架构的核心：7 类场景（早晨带书 / 上课前 10–15min / 上课中 / 下课前 5min / 最后一节课后 / 中午换书 / 晚间查寝未完成）+ None 态；见 `feature/home/domain/HomeScene.kt` 与 `feature/home/domain/SubtitleLibrary.kt` |
| `HomeBootstrap` | 首页进入时的数据预加载协调器（课表 / 查寝等）；见 `feature/home/...` |
| `WGS-84 / BD-09` | 原生定位坐标（WGS-84）与服务端期望坐标（BD-09）的两套坐标系；上传前经 `core/location/CoordinateConverter.kt` 本地转换（WGS-84 → GCJ-02 → BD-09） |
| `publicKey / publicKey2` | RSA 登录密码加密公钥 / 跑步数据加密公钥；两者不可混用；见 `core/auth/RSAUtils.kt` |
| `FIF` | AI 课堂接入的第三方教学平台 (`sttp.fifedu.com`)；独立登录态、独立会话、与校内主站 token 体系不互通；SSO 链路由 `core/network/FifSessionManager.kt` 承载 |
| `RouteTemplate` | 跑步模拟提交用的路线模板资产；以 JSON 文件落地（非 Room），由 `RouteTemplateStore` 管理、`TrajectoryGenerator` 消费、`RouteQualityChecker` 校验 |
| `轻小信 / LightXin` | 本项目名；包名 `com.lightxin` |

## 3. 子系统 / 模块索引

代码按 `core + feature + navigation` 三层组织。

### 3.1 core/（跨功能基础设施）

- `network/` — 多域名 Retrofit（**8 个 `@Qualifier`** = 7 Retrofit [Auth / Main / Csh / Checkin / Sports / Labor / **Fif**] + 1 OkHttpClient [**FifOkHttpClient**]）、`AuthInterceptor`、`TokenRefreshInterceptor`、**`FifSessionManager`**（AI 课堂独立 SSO + CookieJar）；详见 [`network-overview.md`](network-overview.md)
- `auth/` — `RSAUtils`（两组公钥）、`TokenManager`、`SessionManager`
- `location/` — `LocationProvider`（原生 `LocationManager` 封装）、`CoordinateConverter`（WGS-84 → GCJ-02 → BD-09 全链路，无地图 SDK）
- `designsystem/` — `Theme / Color / Type / Shape` + 11 个通用组件文件（`LxCard` / `LxButton` 等）；详见 [`designsystem-overview.md`](designsystem-overview.md)
- `settings/` — `DeveloperPrefs`（调试开关）

### 3.2 feature/（9 个功能包，按 `data / domain / ui` 三层）

| 功能包 | 说明 |
|---|---|
| `login` | 登录 + RSA 密码加密 + Token 存取 |
| `home` | 首页容器 + 底部导航 + 场景驱动叙事（`HomeScene` / `SubtitleLibrary` / `HomeBootstrap` 预加载）；详见 [`home-overview.md`](home-overview.md) |
| `schedule` | 周课表 + 三态周选择器（当前 / 选中 / 其他） |
| `checkin` | 查寝签到：任务列表 / 详情 / 照片上传 / 提交 |
| `running` | 跑步：GPS 真实模式（前台 `RunTrackingService`）+ 模拟模式（`TrajectoryGenerator`）+ **路线模板系统**（录制 / 列表 / 详情 / 质量校验 / 设置页）；详见 [`running-overview.md`](running-overview.md) |
| `labor` | 劳动教育：工时总览 + 活动记录 + 详情（只读） |
| `aiclass` | AI 课堂：FIF SSO + 课程列表 + 数字码签到 + **ML Kit** 扫码签到 |
| `onboarding` | 首启欢迎页（含插画动效） |
| `about` | 关于页 + 调试开关入口 |

### 3.3 navigation/

`NavGraph.kt` 下 18 条路由，覆盖上述 feature 的所有入口与子页（包括 4 条跑步路线模板路由 / 2 条 AI 课堂路由 / 2 条查寝路由 / 4 条跑步主链路由 / 2 条劳教路由 + Home / Login / Onboarding / About / Schedule）。详见 [`navigation-overview.md`](navigation-overview.md)。

> 模块级专项架构文档已全部覆盖（2026-04-22 补齐 8 份，累计 12 份）。当前已有：
> - [`network-overview.md`](network-overview.md) — 多域名层 + FIF 独立会话
> - [`running-overview.md`](running-overview.md) — 真实跑步 / 模拟提交 / 路线模板三条主链
> - [`home-overview.md`](home-overview.md) — 启动预加载 + 场景判定 + 副标题与首页骨架
> - [`designsystem-overview.md`](designsystem-overview.md) — Token 两层结构 + 11 个通用组件 + Compose Dark 与资源层 forceDarkAllowed 的正交关系
> - [`login-overview.md`](login-overview.md) — RSA 加密登录 + Token 持久化
> - [`schedule-overview.md`](schedule-overview.md) — 周课表 + 三态周选择器
> - [`checkin-overview.md`](checkin-overview.md) — 查寝签到全流程（定位/拍照/提交）
> - [`labor-overview.md`](labor-overview.md) — 工时总览 + 活动记录（只读）
> - [`aiclass-overview.md`](aiclass-overview.md) — FIF SSO + 数字码/扫码签到
> - [`onboarding-overview.md`](onboarding-overview.md) — 首启欢迎页 + Canvas 插画动效
> - [`about-overview.md`](about-overview.md) — 品牌展示 + 调试开关
> - [`navigation-overview.md`](navigation-overview.md) — 18 条路由 + 起始页决策 + ViewModel 共享

## 4. 关键架构决定

以下决定已在项目早期拍板并沉淀到代码，未来改动前需先回读。每条后面附 compound decision 链接，完整理由和替代方案见对应文档。

- **全 Compose 原生重写**，不保留任何 WebView 壳（与原 APP 的 H5 + 原生壳模式做根本区别）→ [`decision-compose-native-rewrite`](compound/2026-04-22-decision-compose-native-rewrite.md)
- **Clean Architecture 三层（`data / domain / ui`）按 feature 分包**，不按层分包 → [`decision-feature-tri-layer-package`](compound/2026-04-22-decision-feature-tri-layer-package.md)
- **多域名网络层** — Hilt `@Qualifier` 区分 7 个 Retrofit 实例，共享同一个 `OkHttpClient`；**FIF 除外**：独立 `OkHttpClient` + `CookieJar`（SSO + SESSION 管理必须独立）；认证方式差异由 `AuthInterceptor` 按 URL 路由 → [`decision-fif-independent-okhttp-stack`](compound/2026-04-22-decision-fif-independent-okhttp-stack.md)
- **不引入任何地图 SDK** — 定位走原生 `LocationManager`；跑步仪表盘不展示地图轨迹；坐标转换手写 → [`decision-no-map-sdk`](compound/2026-04-22-decision-no-map-sdk.md)
- **路线模板用 JSON 文件存储而非 Room** — 保持轻量、避免 DB schema 迁移成本 → [`decision-route-template-json-not-room`](compound/2026-04-22-decision-route-template-json-not-room.md)
- **扫码用官方 ML Kit**，不用 ZXing → [`decision-ml-kit-barcode`](compound/2026-04-22-decision-ml-kit-barcode.md)
- **首页从"4 卡 dashboard"改为"此刻我该做什么"叙事结构**（Phase 9C 核心改动）→ [`decision-home-narrative-structure`](compound/2026-04-22-decision-home-narrative-structure.md)
- **线性底栏图标**（Phase 9B）—— 底栏用 `Icons.Outlined.*`，不切 `Filled.*`，靠陶土色高亮区分选中；主题同时支持 Light / Dark（Compose 层跟随系统），资源层 `forceDarkAllowed=false` 防厂商强制反色滤镜
- **设计语言** — Anthropic 暖色 / 羊皮纸质感；陶土色使用密度有准则（一屏最多 2 次）→ [`decision-warm-parchment-visual-language`](compound/2026-04-22-decision-warm-parchment-visual-language.md) / [`decision-terra-color-usage-density`](compound/2026-04-22-decision-terra-color-usage-density.md)
- **字体三字族**：Newsreader（衬线拉丁）+ Outfit（sans 数字）+ Noto Serif SC（中文衬线 fallback，只用 Regular）→ [`decision-triple-typeface-cjk-fallback`](compound/2026-04-22-decision-triple-typeface-cjk-fallback.md)

## 5. 已知约束 / 硬边界

- **项目级硬约束**：见根目录 `AGENTS.md`（Gradle 命令、代码风格、命名约定、提交规范、安全要点）
- **接口协议不可自创** — 所有对外请求必须与原 APP 抓包一致；字段名存在拼写错误（如 FIF 的 `couseItemId`）也保持原样
- **查寝接口鉴权** — 不是单一 Bearer token，需要同时带 `accessToken / access_token / userCode / xh / userType / X-Requested-With / Origin / Referer`，否则返回 `-100 非法访问` → [`decision-checkin-multi-header-auth`](compound/2026-04-22-decision-checkin-multi-header-auth.md)
- **FIF 会话独立** — AI 课堂必须用独立 `OkHttpClient + CookieJar`，不能复用校内主站 `AuthInterceptor` → [`decision-fif-independent-okhttp-stack`](compound/2026-04-22-decision-fif-independent-okhttp-stack.md)
- **跑步数据加密** — 字段名和字段值**都要**用 `publicKey2` RSA 加密，不是只加密值 → [`decision-running-dual-rsa-encryption`](compound/2026-04-22-decision-running-dual-rsa-encryption.md)
- **不引入任何地图 SDK** — 定位走原生 `LocationManager`，坐标转换手写 → [`decision-no-map-sdk`](compound/2026-04-22-decision-no-map-sdk.md)
- **HAR / local.properties / 密钥 / 个人 token 等敏感数据禁止提交**（见 AGENTS.md）

---

> 本文档反映 2026-04-21 真实代码状态。`docs/项目规划/` 下五份文档（`项目规划.md` / `AI课堂开发规划.md` / `路线模拟实现文档.md` / `正式版预期功能.md` / `homepage-design-spec.md`）多数已是"完成记录"而非未来方向；`docs/接口分析/` 下四份文档仍是长期有效的协议参考与踩坑实录。onboarding 的文档归并建议见会话汇报。
