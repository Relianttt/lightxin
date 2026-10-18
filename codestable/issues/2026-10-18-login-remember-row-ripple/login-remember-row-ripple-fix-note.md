---
doc_type: issue-fix
issue: 2026-10-18-login-remember-row-ripple
status: fixed
severity: minor
summary: 登录页「记住密码」行去掉铺满整行的 Material 水波，改为与设计系统一致的按压底色
tags: [login, compose, ripple, design-system, ui]
---

# Fix Note：登录页「记住密码」整行水波动画

## 现象

登录页点「记住密码」这一行（文字或右侧空白区域，不是点复选框本身）时，会出现一条灰色半透明色块
从按压点向外铺满整行的动画，看起来像"整排闪一下"，和页面其它交互的观感不一致。

## 根因

`feature/login/ui/LoginScreen.kt` 的 `RememberPasswordRow` 直接用了 `Modifier.clickable {}` 默认
indication，即 Material 水波（ripple）。该行是 `fillMaxWidth()`，水波的圆形扩散被裁剪成整行矩形，
形成"整排"的灰色高亮。

本项目设计系统的其它可点元素（`LxButton` / `LxMenuRow` / `LxIconButton` / `LxChoiceChip` /
`LxFloatingActionButton`）统一走 `indication = null` + `animatedPressColor` 的按压底色反馈，
这一行是漏网的唯一默认水波。

## 修复

`RememberPasswordRow` 改为设计系统的按压手感：

- `MutableInteractionSource` + `clickable(indication = null, interactionSource = ...)`，去掉水波；
- `animatedPressColor(rest = Color.Transparent, pressed = LxCream)` 做整行的浅底色渐变，
  配合 `clip(RoundedCornerShape(LxRadiusSm))` 与卡片圆角语言一致；
- 加 `role = Role.Checkbox`，无障碍语义与复选框对齐；点击文字 / 空白仍可切换勾选。

复选框本身（Material `Checkbox`）保留其自带的小范围水波，未做改动。

## 验证

- `JAVA_HOME=/opt/android-studio/jbr ./gradlew -Dorg.gradle.java.home=/opt/android-studio/jbr :app:assembleDebug`：
  BUILD SUCCESSFUL。
- 真机（Pixel 4 / Android 13）按住该行 1.6s 截图：按压态是浅羊皮纸底色，无灰色整行色块；
  松开后底色淡出。
- 交互回归：点文字切换勾选、直接点复选框切换勾选、勾选状态随登录提交保存均正常。
- `:app:testDebugUnitTest`：129 个用例，0 失败；`:app:lintDebug`：BUILD SUCCESSFUL。
