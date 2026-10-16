# 验证对照表

按改动范围选最小足够的检查；已通过的不重跑。命令在仓库根目录执行：Git Bash / Linux 用 `./gradlew`，PowerShell 用 `.\gradlew.bat`。当前 Linux JDK 启动方式见 `AGENTS.md` 的「编译与构建」；SDK 使用本机 `local.properties`。

| 改动范围 | 检查 | 证据 |
|---|---|---|
| 单个类的逻辑（domain / data / mapper / parser） | `./gradlew testDebugUnitTest --tests "com.lightxin.<包>.<类>Test"` | 测试输出 |
| 跨模块逻辑、共享网络层（`core/network/`） | `./gradlew testDebugUnitTest` | 测试输出 |
| UI / Compose | `./gradlew assembleDebug assembleDebugAndroidTest` + 真机或模拟器上的 `./gradlew connectedDebugAndroidTest` | APK 编译输出、设备测试输出及截图或录屏；仅编译不代表交互通过 |
| 网络协议字段、鉴权、加密 | 对应单元测试 + 与抓包请求逐字段对比 | 字段对比 |
| 相机、导航、登录等依赖运行时的流程 | `./gradlew connectedAndroidTest`（需连接设备） | 测试输出 |
| 构建配置、依赖、混淆规则 | `./gradlew assembleDebug lint`；混淆相关加 `assembleRelease` | 构建输出 |
| `.agents/notes/` | `python ~/.agents/skills/dsh-workflow/scripts/check_notes.py .agents/notes --agents AGENTS.md` | 脚本输出 |
