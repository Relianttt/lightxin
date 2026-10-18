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
Recent history uses concise Chinese task-oriented subjects, often with phase tags, for example `完成Phase9C UI精炼：首页叙事架构 + 我的页精简`. Keep commits focused on one change set. Commit messages must include both a subject line and a body; do not create title-only commits. The body is for recording changes and validation only: keep it short and factual, preferably as terse bullets; do not write long narrative explanations or reasoning (those belong in notes/docs). PRs should include: what changed, affected screens/modules, commands run, and screenshots or recordings for UI updates. Link related docs or issues when the change is tied to a plan in `docs/`.
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

## 动画设计哲学
动画首先是沟通，不是装饰；每个动画必须服务于状态反馈、空间连续、视觉引导、层级表达或交互理解中的至少一项。详细参考：[Claude.ai 动画设计哲学及规范指导](docs/讨论方案/Claude.ai动画设计哲学及规范指导.md)。
- 优先编排页面首次入场、重要状态转换和关键反馈；同一场景只保留一个主要运动焦点，避免所有卡片、按钮和图标同时运动。
- 移动端优先保证导航方向、操作反馈、信息可见性和低性能稳定性；动画不能阻塞点击、输入、滚动或回退。
- 默认使用小位移（约 4–12dp）与透明度 / transform 过渡；避免大幅飞入、3D 翻转、持续呼吸、无意义闪烁和逐行长列表 stagger。
- 入场使用自然减速的 ease-out，出场通常短于入场；错峰只用于少量首屏重点元素，并限制总延迟。
- 组件状态变化应连续且可中断；展开、收起、弹层和标签切换要保持触发位置与空间关系，不能用突然替换制造跳变。
- 所有动效参数集中在 `core/designsystem/theme/LxMotion.kt`；具体时长是工程参考值，不把本文参考区间冒充 Claude.ai 官方 Token。
- 默认不使用明显回弹 / overshoot；除非有明确的可理解性收益并经过真机验证，否则使用临界阻尼或无 overshoot 曲线。
- 必须尊重系统减少动态效果设置；删除动画后功能仍须完整可用，必要反馈不能依赖动画是否播放。
- 动效评审至少检查：目的、单一焦点、响应及时、方向连续、文字可读、滚动位置稳定、低端机表现、可访问性和 reduced-motion 兜底。

## 项目碎片知识

<!-- cs-note managed: 用 cs-note 维护，新条目按下面分节追加 -->

### 编译与构建
- 当前 Linux 环境已安装 Android Studio，可用 `JAVA_HOME=/opt/android-studio/jbr ./gradlew -Dorg.gradle.java.home=/opt/android-studio/jbr <任务>`；命令行覆盖仓库 `gradle.properties` 中的 Windows JDK 路径，SDK 路径由本机忽略的 `local.properties` 配置。
- Coil 3 加载网络图片需要同时依赖 `coil-network-okhttp`；只加 `coil-compose` 时 `AsyncImage` 对 http(s) URL 会静默空白。

### 运行与本地起服务

### 测试

### 命令与脚本陷阱

### 路径与目录约定

### 环境变量与凭证

### 其他
