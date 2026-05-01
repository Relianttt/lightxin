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
Recent history uses concise Chinese task-oriented subjects, often with phase tags, for example `完成Phase9C UI精炼：首页叙事架构 + 我的页精简`. Keep commits focused on one change set. Commit messages must include both a subject line and a body describing the work completed; do not create title-only commits. PRs should include: what changed, affected screens/modules, commands run, and screenshots or recordings for UI updates. Link related docs or issues when the change is tied to a plan in `docs/`.

## Security & Configuration Tips
Do not commit `local.properties`, captured HAR files, tokens, or device-specific data. Keep secrets out of source and route network-related changes through `core/network/` so auth and interceptors stay centralized.

## Hard Constraints
These constraints are non-negotiable. Changing any of them requires consulting the full decision documents linked below.

- **No map SDK** — Location uses native `LocationManager`; coordinate conversion (WGS-84 → GCJ-02 → BD-09) is hand-written in `core/location/CoordinateConverter.kt`. Do not add AMap / Baidu / Tencent map or location SDKs. → `codestable/compound/2026-04-22-decision-no-map-sdk.md`
- **Running data dual RSA encryption** — When uploading running data, both field names AND field values must be encrypted with `publicKey2` via `RSAUtils.encryptSportData()`. Never encrypt only the values. `publicKey2` (running) and `publicKey` (login) are not interchangeable. → `codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`
- **API protocol must match original app** — All external requests must match the original app's captured protocol exactly. Field names with typos (e.g., FIF's `couseItemId`) are kept as-is.
- **Check-in multi-header auth** — The check-in API (`fdygl.aiit.edu.cn`) requires 7 identity fields simultaneously; missing any one returns `-100 非法访问`. → `codestable/compound/2026-04-22-decision-checkin-multi-header-auth.md`
