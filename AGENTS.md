# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app named `LightXin`. Main code lives in `app/src/main/java/com/lightxin`, with shared infrastructure under `core/`, navigation in `navigation/`, and product features under `feature/<feature>/data|domain|ui` (for example `feature/login/ui/LoginScreen.kt`). Resources are in `app/src/main/res`. Planning notes and reverse-engineering writeups live in `docs/`. UI experiments and reference mockups live in `prototype/`. Do not treat `HAR/` as source code; it is ignored local capture data.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root:

- `.\gradlew.bat assembleDebug`: build the debug APK.
- `.\gradlew.bat installDebug`: install the debug build on a connected device/emulator.
- `.\gradlew.bat lint`: run Android lint checks.
- `.\gradlew.bat test`: run local JVM tests.
- `.\gradlew.bat connectedAndroidTest`: run instrumentation tests on a device.

Open the project in Android Studio when working on Compose previews, resources, or emulator flows.

## Coding Style & Naming Conventions
The project uses Kotlin official code style and 4-space indentation (`.idea/codeStyles/Project.xml`). Follow package-first organization and keep feature code split into `data`, `domain`, and `ui`. Use descriptive suffixes such as `Repository`, `ViewModel`, `Screen`, `Api`, and `Models`. Compose components use PascalCase function names, Kotlin files use PascalCase, and Android/XML resources use `snake_case` such as `network_security_config.xml`.

## Testing Guidelines
There are currently no committed `app/src/test` or `app/src/androidTest` directories, so new work should add tests alongside the feature being changed. Put JVM tests in `app/src/test/java/...` and device tests in `app/src/androidTest/java/...`. Name files `SomethingTest.kt` or `SomethingInstrumentedTest.kt`. At minimum, run `lint` and `test` before opening a PR; use `connectedAndroidTest` for camera, navigation, or login flows that depend on Android runtime behavior.

## Commit & Pull Request Guidelines
Recent history uses concise Chinese task-oriented subjects, often with phase tags, for example `完成Phase9C UI精炼：首页叙事架构 + 我的页精简`. Keep commits focused on one change set. Commit messages must include both a subject line and a body; do not create title-only commits. The body must spell out what changed and what validation was run, preferably as short bullets matching recent commit history. PRs should include: what changed, affected screens/modules, commands run, and screenshots or recordings for UI updates. Link related docs or issues when the change is tied to a plan in `docs/`.
When writing multi-line commit messages from PowerShell, use real newlines such as the PowerShell escape `` `n `` or `git commit -F <message-file>`; plain `\n` is committed literally and will not render as a line break.

## Security & Configuration Tips
Do not commit `local.properties`, captured HAR files, tokens, or device-specific data. Keep secrets out of source and route network-related changes through `core/network/` so auth and interceptors stay centralized.

## Hard Constraints
These constraints are non-negotiable. Changing any of them requires consulting the full decision documents linked below.

- **No map SDK** — Location uses native `LocationManager`; coordinate conversion (WGS-84 → GCJ-02 → BD-09) is hand-written in `core/location/CoordinateConverter.kt`. Do not add AMap / Baidu / Tencent map or location SDKs. → `codestable/compound/2026-04-22-decision-no-map-sdk.md`
- **Running data RSA encryption — keys always, values except `runningRoute`** — Field names AND field values are encrypted with `publicKey2` via `RSAUtils.encryptSportData()`; never encrypt only the values. The one exception is `runningRoute`, whose value must go **in plaintext**: it is the only long field (several KB → dozens of concatenated RSA blocks) and the server cannot decrypt that framing, so the record is judged invalid (`remark="运动轨迹异常，无效"`) and the track never appears. Everything else — including other fields that may grow long — stays encrypted. `publicKey2` (running) and `publicKey` (login) are not interchangeable. → `.agents/notes/2026-10-17-decision-running-route-plaintext.md`
- **API protocol must match original app** — All external requests must match the original app's captured protocol exactly. Field names with typos (e.g., FIF's `couseItemId`) are kept as-is.
- **Check-in multi-header auth** — The check-in API (`fdygl.aiit.edu.cn`) requires 7 identity fields simultaneously; missing any one returns `-100 非法访问`. → `codestable/compound/2026-04-22-decision-checkin-multi-header-auth.md`

## Workflow
决策与经验：`.agents/notes/`；验证对照表：`.agents/verify.md`；历史文档（只读）：`codestable/`
- 非平凡改动动手前：按 dsh-workflow 检索 notes 与铁律，取舍先确认。
- 提交前：按 dsh-workflow 选最小足够验证，commit body 写命令与结果。
- 改了行为/架构/协议/格式：按 dsh-workflow 同步或新建 note。
- 同一错误第二次出现：按 dsh-workflow 沉淀为 pitfall note 或门禁。

## 项目碎片知识

<!-- cs-note managed: 用 cs-note 维护，新条目按下面分节追加 -->

### 编译与构建
- 当前 Linux 环境已安装 Android Studio，可用 `JAVA_HOME=/opt/android-studio/jbr ./gradlew -Dorg.gradle.java.home=/opt/android-studio/jbr <任务>`；命令行覆盖仓库 `gradle.properties` 中的 Windows JDK 路径，SDK 路径由本机忽略的 `local.properties` 配置。

### 运行与本地起服务

### 测试

### 命令与脚本陷阱

### 路径与目录约定

### 环境变量与凭证

### 其他
