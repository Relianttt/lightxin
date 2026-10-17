---
doc_type: decision
category: architecture
status: active
date: 2026-10-17
tags: [auth, session, fif, aiclass, home, login, keystore]
---

## 背景

本项目有两套登录态：校内的 `access_token`（`TokenManager`）与 AI课堂的 FIF 会话（`FifSessionManager`）。失效原因不同，处置也不同，但此前界面上只有一个笼统的「登录信息已失效，请重新登录」。

最容易误判的是 FIF：同一账号在官方 App 登录后，服务端的 FIF 用户映射会被顶掉。本地之所以察觉不到 —— **FIF token 与 `studentId` 持久化在 `fif_session` DataStore，而 SESSION Cookie 只在内存**，进程存活期间 `isSessionValid()` 恒为 true，`ensureSession()` 直接短路。于是 App 一路装死，直到用户站在教室门口点扫码，才被服务端一句 `FIF用户重映射失败` 打回。

同源 fork（`SDX8/lightxin`）已经踩过并解决了这个问题，本 note 记录我们采纳的方案与两处刻意不同的取舍。

## 决定

### 1. 失效分三档，每档只说自己能证实的

| 档 | 判据 | 处置 |
|---|---|---|
| 校内 token 过期 | HTTP 401 → `TokenRefreshInterceptor` 用 refresh_token 重试 | 自动续期；刷新失败才提示重登 |
| 本地没有凭据 | `access_token` / `userCode` 为空 | 首页「登录状态已失效」横幅 |
| FIF 映射被服务端拒绝 | **主动探测**得到 `FifMappingReason.ACCOUNT_INFO`（或全新 token 收到 401/403） | 首页「账号已在其他设备登录」横幅 |

**其余一切情况一律沉默**：`IOException`、协程超时、HTTP 5xx、解析不出结构的 `UNKNOWN`，都归 `Inconclusive`，不提示。误报的代价高于漏报 —— 把断网说成"你被人顶号了"，用户会去做一次毫无意义的重新登录，还要把官方 App 顶下线。

### 2. 探测规则

- `AiClassSessionGuard.checkOnLaunch()`：12s 上限，四态之外多一个 `LoginExpired`。
  `Skipped`（从没进过 AI课堂）与 `Inconclusive` 都不提示；`Healthy` 顺带把会话续上。
- **`Skipped` 的判据是「用过 AI课堂」，不是「有 FIF token」**（`FifSessionManager.isFifUsed()`，由 `performSso()` 置位）。被顶号时 SSO 失败、token 根本不会落盘，用 token 当判据会把「用过但正被顶」误判成「从没用过」—— 横幅永远不亮，只有进 AI课堂才看得到卡片（这是 2026-10-17 实测踩到的坑）。
- 结论一律写日志（不止失败），否则横幅误报/漏报时没有第一手依据。
- 调用点只有两处：`HomeViewModel` 的 `init` 与 `refresh()`，各起独立协程 —— 12s 的最坏情况不进 splash 放行、不占下拉刷新动画。
- **探测不得破坏本地会话**：走 `FifSessionManager.probeSession()`（跑完整链路但不预清 DataStore 与 Cookie），**禁止**用 `performSso(forceRefresh = true)` 当探针 —— 它会先清本地会话再跑链路，探测时一旦断网，用户就真的被自己踢下线了。
- 探测失败原因写日志（tag `AiClassProbe`），否则横幅误报时无从判别。

### 3. 顶号横幅是降级提示，不是阻断

只声明「AI课堂签到不可用」，课表、查寝、跑步照常使用。点「重新登录」先弹二次确认，明说**会挤下线官方 App、之后要回官方 App 再登一次**，并提示只想看课表可以先不登录；确认后才走重登。

### 4. 重登只有一条路径

横幅复用既有的 `FifReloginViewModel`（`resetFifSession` → `sessionManager.logout` → `homeBootstrap.resetAfterRelogin` → 登录成功后回载首页），不新开一条跳转链。`navigation/NavGraph` 已经把这条路径抽成 `onFifRelogin`，横幅接的是同一个 lambda。

### 5. 退出登录必须同时清 FIF 会话

`ProfileViewModel.logout()` 原先只调 `sessionManager.logout()`（只清 `TokenManager`），FIF 的 DataStore 与 Cookie 全留着 —— 换账号登录会带着上一个人的 AI课堂身份。现在改为 `logout()` + `fifSession.clear()`。

### 6. 劳动教育的「非法访问」= 校内登录失效，不是 FIF 卡片

`ldjy` 用 **HTTP 200 + `{"flag":false,"result":"非法访问"}`** 表达「token 无效」（实测：`curl` 带无效 accessToken 即得此响应）。此前它会一路冒到界面上，用户只看到「非法访问」四个字，也不知道该干什么。

处置：`LaborRepository` 用 `isLaborAuthRejection(flag, result)` 识别，转成 `AuthExpiredException`（`core/network`，带 `technicalDetail`），首页横幅升级为 `SessionAlert.LoginExpired`；劳动教育页出 `ServiceErrorCard`（与 AI课堂/查寝同一版式）：标题「暂时无法加载劳动教育」，三个出口「重新加载 / 重新登录 / 查看错误详情」，详情写 `LABOR_REJECTED` + 操作名 + `flag=false / result=非法访问`。重登走 `onSessionRelogin`，不清 FIF。

**不套用 AI课堂那张卡片**：那是 FIF 专有文案 + FIF 恢复流程（`FifMappingErrorCard` / `FifMappingRecovery`），而 labor 失效的是校内 token，混用会让用户去重建一个没坏的 FIF 会话。

也不能一刀切按字面「非法访问」判定 —— 查寝 `fdygl` 的 `-100 非法访问` 是「7 个身份字段没带全」，与登录无关，那条仍归 `CheckinRepository` 自己处理。

### 7. 查寝的「非法访问」用同一张卡片模板，文案不同

查寝 `fdygl` 把鉴权失败塞进 200：现场实测是 **`{"code":"0","flag":false,"msg":"非法访问"}`**（文档里记的 `-100` 反而是另一种形态）。此前 `code == "0"` 被判成功、`rows`/`data` 为 null → **渲染成「暂无签到任务」，错误被吞掉**——用户以为没安排，实际是被拒。

处置：

- `CheckinRepository` 用 `isCheckinRejection(flag, msg)`（`flag == false` 且 msg 含「非法访问」）判定，抛 `CheckinRejectedException`，把 `code/msg` 原文收进技术详情。
- 界面用 `ServiceErrorCard` —— 就是 AI课堂那张卡片的版式，只换文案：标题「暂时无法加载查寝信息」，正文说明「可能是登录过期，也可能只是这一下没对上，先重试，一直这样就重登」，底部仍保留「重试 / 重新登录 / 查看错误详情」三个出口。
- **重登不清 FIF**：查寝坏的是校内登录态，走 `onSessionRelogin`（`logout` + `resetAfterRelogin`）；AI课堂那条仍走 `onFifRelogin`（额外 `resetFifSession`）。

### 8. 服务不可用卡片只保留一份版式

原来只有 `FifMappingErrorCard`。现在把版式抽成 `ServiceErrorCard`（`core/designsystem`），`FifMappingErrorCard` 退化成薄包装（文案与动作字符串一字未改，`RecoveryUiInstrumentedTest` 依赖它们）。新模块要同样的体验时**只提供措辞**，不要复制一份版式——复制出去之后任何视觉调整都会漏掉一半入口。

### 9. 登录成功必须重跑首页 bootstrap

`HomeBootstrap.load()` 在 `onCreate` 就被调用，那时还没 token；它当时把 `loaded = true` 写死在 token 检查之前，于是登录成功后再调也没用 —— 首页永远停在「正在加载…」，只有下拉刷新（走 `HomeViewModel` 自己的 loader）才能出数据。两处一起改：

- `load()` 未登录时不置 `loaded`，只放行 splash（`_ready = true`）；
- 登录成功统一走 `FifReloginViewModel.onLoginSuccess()`，总是 `resetAfterRelogin()` + `load()`，不再靠 `reloadHome` 标记只给 FIF 重登用。

### 10. 记住密码只做在账号维度

- 只记住**登录页的学号与密码**，供下次进登录页预填；不记住任何 token 派生凭据、不做免密登录。
- 密码先经 AndroidKeyStore 的 AES-256-GCM 密钥加密再落 `lightxin_login` DataStore，**不存明文**；密钥不出该设备，导出 DataStore 文件也解不开。
- 解密失败（换机、恢复备份导致密钥被回收）一律降级成「没记住」并删掉密文，不每次启动白试。
- 安全边界写清楚：它只挡「别人捡到已解锁的手机」这一层，不挡本机其他进程。
- **不引入调试口令类功能**（关于页调试开关的口令校验不搬）。调试面越小越好，账号层面才需要便利。

## 考虑过的替代方案

- **用 `performSso(forceRefresh = true)` 当探针**：不用新写方法，但会先清本地会话；断网探测即等于自我踢下线，否决。
- **沿用 fork 的异常分类（非 `IOException` 就算顶号）**：他的实现只能靠异常类型猜。我们有 `FifUserMappingException(reason)`，按 `reason` 分类更准，且不会把"校内凭据缺失"或"服务端 5xx"说成顶号。
- **探测到 `Inconclusive` 也提示"可能被顶号"**：能提高召回，但会把断网变成一句错误指控，否决。
- **重登不弹二次确认**：少一步操作，但用户会在不知情的情况下把官方 App 顶下线，否决。
- **把密码存进 Keystore 保护的 DataStore 明文**：省一层加解密，但一次 `adb backup` / 文件泄露就等于泄露密码，否决。
- **给调试开关加口令**：用户明确不做。

## 后果

- 首页可能多出两条横幅之一（「登录状态已失效」或「账号已在其他设备登录」），都是降级提示，不阻断浏览。两者不会同时出现：`LoginExpired` 优先且粘性（实测同一时刻 labor 报非法访问、FIF 体检报 Blocked，首页显示前者）—— 一次重登同时解决两者。
- 冷启动与每次下拉刷新各多一次 SSO 往返（12s 上限、独立协程，不影响首屏时序）；探测成功时顺带刷新 FIF Cookie，签到链路反而更稳。
- `AiClassSessionGuard` 的分类**已在真机上用真实顶号复现**（2026-10-17）：官方 App 在别处登录后，`AiClassProbe` 给出
  `Blocked；原因链：Exception(AI课堂会话体检失败: FIF 用户映射失败) <- FifUserMappingException <- ClassCastException(JsonNull cannot be cast to JsonObject)`
  —— SSO 第一步 `aiitpass` 回跳的 `Location` 里 `token=` 为空，随后 `getAiktUserIdByMemberId` 回 `{"data":null,"status":0,"message":"鉴权失败，Token为空"}`，解析取不到 `data` 即 `ACCOUNT_INFO`。若日后横幅不响，先看这条日志里实际的 reason，再补分类，不要放宽成"网络失败也提示"。
- 探测本身会重新跑一次 SSO，等于每次冷启动都重建一次服务端映射。如果 FIF 映射也是"单端独占"，那这个探测理论上可能反过来把官方 App 的 AI课堂会话顶掉；官方 App 走的是另一条 SSO 路径，是否受影响**未验证**。真出现"用了本 App 官方 App 就登不上 AI课堂"，先停掉探测，退回到只有失败时才提示。
- 记住密码把密码的可用性交给了设备锁：能解锁手机的进程就能解密。这条已写进代码注释，别在它上面再叠"免密登录"之类的东西。

- 查寝的 `-100 非法访问` 与劳动教育的 `非法访问` 是两套语义（后者可按登录失效处理，前者是身份字段问题）；但现场实测查寝回的是 `code:"0"` + `flag:false`，说明「code 为 0 即成功」这条旧假设本身就不成立。以后新增 `fdygl` 接口时，`flag` 与 `msg` 必须一起判。
- `ServiceErrorCard` 现在是三个模块共用的入口（AI课堂、查寝、劳动教育）。它的正文要求是「发生了什么 / 是不是你的错 / 现在能做什么」，把服务端原文（`非法访问`、`-100`）只放进「错误详情」。

## 相关文档

- 同源 fork 的对应实现与结论：`SDX8/lightxin` 提交 `706fd1e`、`622f097`
- FIF 会话与映射失败恢复：[`2026-10-17-decision-fif-mapping-recovery.md`](2026-10-17-decision-fif-mapping-recovery.md)
- 跑步上传的同类教训（成功信号要回读、不要相信第一层返回）：[`2026-10-17-decision-running-route-plaintext.md`](2026-10-17-decision-running-route-plaintext.md)
- 代码：`core/network/FifSessionManager.probeSession()`、`feature/aiclass/data/AiClassSessionGuard.kt`、`feature/home/ui/HomeViewModel.kt`、`core/auth/RememberedLoginStore.kt`
