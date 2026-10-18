---
doc_type: decision
category: convention
status: active
date: 2026-10-18
tags: [auth, login, home, relogin, session]
---

## 背景

2026-10-17 的登录态失效方案里，首页两条降级横幅（「登录状态已失效」与「AI课堂映射被顶」）点「重新登录」会先弹二次确认，明说会把「爱信校园」官方 App 挤下线、之后要回官方 App 再登一次。

2026-10-18 用户明确要求去掉这个弹窗，认为重登是用户主动点的按钮，再确认一次是多余步骤；同时把失效横幅文案改为「当前登录信息已过期，部分功能已失效，请重新登录以继续使用校园服务。」，把「重登会挤下线官方 App」的后果从弹窗挪回横幅正文。

## 决定

- 删除 `HomeDashboard` 的 `showReloginDialog` 状态与 `LxDialog` 二次确认弹窗。
- 两条横幅的「重新登录」直接调用既有重登路径（`onFifRelogin`），不再经过确认。
- 首页「登录状态已失效」横幅文案加入「部分功能已失效」，其余句子不动。

## 理由

- 用户拍板：重登是显式点击的动作，二次确认多余。
- 横幅本身已有「请重新登录」，弹窗只是在重复告知；去掉后路径更短。

## 考虑过的替代方案

- **保留弹窗、只缩短文案**：用户明确说不需要这个弹窗，否决。
- **弹窗加「不再提示」**：要新增持久化开关，用户没提这个需求，不采用。
- **把「会挤下线官方 App」写进横幅正文**：横幅会变长，用户只要求补「部分功能已失效」，未采用。

## 后果

- 点「重新登录」会直接进入登录页，并可能挤下线官方 App，不再提供「先不登录」的退路；用户接受。
- AI课堂映射被顶横幅同样直接跳登录，不再给出「只想看课表可以先不登录」的提示。
- 旧决策 `.agents/notes/2026-10-17-decision-login-expiry-surfaces.md` 的「重登不弹二次确认」替代方案与 §3 二次确认被本条翻案。

## 验证

- `assembleDebug` 成功；`testDebugUnitTest` / `lintDebug` 结果见提交 body。
- 纯接线与文案改动；失效横幅需要清空 token 才能复现，未在真机触发。

## 相关文档

- 登录态失效三档：`.agents/notes/2026-10-17-decision-login-expiry-surfaces.md`
- 代码：`feature/home/ui/HomeDashboard.kt`、`feature/home/ui/MainSessionExpiredBanner.kt`
