---
doc_type: learning
track: knowledge
date: 2026-05-03
slug: kotlin-nullability-cleanup
component: [kotlin, ui, repository, navigation]
tags: [kotlin, nullability, non-null-assertion, compose, refactor]
---

# Kotlin 空值收敛的安全做法

## 背景

这轮清理的目标不是追求语法洁癖，而是把项目里反复出现的 `if (x != null) Foo(x!!)` 模板换成编译器可验证的非空表达。涉及 UI 错误态、导航起始页、相机回调、跑步模板前置条件、AI 课堂响应字段。

收获是：清 `!!` 不能机械替换成 `?.let`。更稳的做法是先明确“这个值为空时业务语义是什么”，再把非空值提取成局部变量，让后续分支只消费这个局部变量。

## 推荐模式

### 1. UI 层：优先用局部变量或 `?.let`

错误文案、选中对象、临时 Uri 这类 UI 状态，通常已经允许为空。写法应让空值自然跳过渲染或动作：

```kotlin
val error = uiState.error
when {
    error != null -> LxError(message = error)
}

tempPhotoUri?.let { uri -> cameraLauncher.launch(uri) }
```

这比 `uiState.error!!` 更适合 Compose，因为状态读取和渲染之间可能跨过重组边界；局部变量也能让 Kotlin smart cast 更稳定。

### 2. API 响应：先提取业务 id，再分支

后端响应对象本身为空、字段为空、字段空字符串，通常都代表“当前没有可用业务对象”。推荐先用 `takeIf` 提取有业务意义的 id：

```kotlin
val signId = data?.signId?.takeIf { it.isNotBlank() }
if (signId == null) {
    Result.success(null)
} else {
    Result.success(AiSignInInfo(signId = signId))
}
```

这样保留原语义：空就是无活动签到；非空才构建 domain model。不要先构建半空对象再靠调用方兜底。

### 3. 前置条件：给条件结果命名

当一个布尔条件实际是在证明某个值可用时，不要把证明留在人脑里：

```kotlin
val templatePoints = overridePoints?.takeIf { it.size >= 2 }
val submitCall = if (templatePoints != null) {
    repository.submitSimulation(overridePoints = templatePoints)
} else {
    repository.submitSimulation(config)
}
```

`templatePoints` 这个名字同时表达了两件事：它来自模板，并且已经满足“至少 2 个点”的前置条件。后续代码不需要再用 `overridePoints!!` 复述这条证明。

### 4. 起始页 / 导航：先绑定非空局部值

`collectAsState(initial = null)` 是合理的启动占位设计，但进入 `NavHost` 前要把 nullable route 变成局部非空 route：

```kotlin
val resolvedStartRoute = startRoute
if (resolvedStartRoute == null) {
    LxLoading()
    return
}

NavHost(startDestination = resolvedStartRoute)
```

对导航共享 ViewModel，`getBackStackEntry()` 还要放进 `remember(backStackEntry) { ... }`，避免 composition 中裸调用。

## 不适用边界

- 如果空值代表真正的协议损坏，而不是“无数据”，应该返回 failure 或记录错误，不要静默 `orEmpty()`。
- 如果字段名来自抓包协议，不能为了“类型好看”改接口模型字段名。
- 如果 `!!` 是在证明生命周期强约束，先找出前置条件归谁维护，再决定是否改成局部变量、guard clause，或显式失败。
- 不要只为清零统计而牺牲业务语义。`rg "!!"` 是自检工具，不是设计目标。

## 自检清单

1. 空值分支的业务语义有没有被保留？
2. 非空值是否被命名成局部变量，而不是散落在多个条件里？
3. `takeIf { it.isNotBlank() }` 是否只用于真正的业务 id / 文案，而不是掩盖协议错误？
4. 改完是否运行 `rg` 搜索剩余非空断言，并跑 `test` / `lint`？

## 代码锚点

- `navigation/NavGraph.kt:71-81` — 起始页先绑定 `resolvedStartRoute`
- `navigation/NavGraph.kt:194-380` — 共享 ViewModel 的 parent entry 通过 `remember(backStackEntry)` 获取
- `feature/checkin/ui/CheckinDetailScreen.kt:138-143` — 相机 Uri 用 `?.let`
- `feature/running/ui/RunningViewModel.kt:210-237` — 模板轨迹点提取为 `templatePoints`
- `feature/aiclass/data/AiClassRepository.kt:112-124,140-150` — AI 课堂响应先提取非空 `courseRecordId` / `signId`
- `core/designsystem/component/LxLoadingState.kt:136-205` — 局部错误 / 空态组件下沉，减少页面私有 `ErrorHint` 复制
