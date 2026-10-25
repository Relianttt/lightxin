---
doc_type: learning
track: pitfall
status: active
date: 2026-10-25
tags: [release, r8, gson, crash, schedule]
---

## 现象

release 包（`isMinifyEnabled = true`）登录后立刻闪退，debug 包正常。crash 日志：

```
ExceptionInInitializerError
  at ScheduleRepository$refreshSemester...
Caused by: java.lang.IllegalStateException: TypeToken must be created with a type argument:
new TypeToken<...>() {}; When using code shrinkers (ProGuard, R8, ...) make sure that
generic signatures are preserved.
  at ScheduleCacheMapper$weekType$1.<init>
```

## 根因

代码用匿名子类创建 Gson 类型：

```kotlin
private val weekType = object : TypeToken<List<Int>>() {}.type
```

`TypeToken` 在运行时通过 `getGenericSuperclass()` 读取泛型签名。R8 收紧后匿名子类的 `Signature` 属性丢失（现有 proguard 规则里的 `-keepattributes Signature` 不足以保住它），`TypeToken` 拿不到类型参数，构造时直接抛异常，初始化该 object 的 `<clinit>` 失败。debug 包不混淆，所以只在 release 复现。

## 做法

- 改用 `TypeToken.getParameterized(List::class.java, X::class.java).type`：类型信息直接由 Class 对象组合，不依赖泛型签名。已改 `ScheduleCacheMapper.weekType`、`LaborViewModel.activityListType`、`CreditViewModel.creditRecordsType`。
- `proguard-rules.pro` 追加 `-keep class com.google.gson.reflect.TypeToken { *; }` 与 `-keep class * extends com.google.gson.reflect.TypeToken { *; }` 兜底，防止以后新增匿名 TypeToken 又踩。

## 预防

- 凡是给 Gson / Room converter 传 `Type`，用 `getParameterized`，不要用匿名 `object : TypeToken<...>() {}`。
- 只验证 `assembleDebug` 不足以发现这类问题：涉及 Gson 反射、泛型、序列化的改动要在 release 包上跑一次真实登录流程。本次 release 闪退就是卸载重装后登录才暴露。

## 验证

`assembleRelease` 通过；`adb install -r` 覆盖安装后冷启动，`MainActivity` 保持存活，crash 缓冲区为空。劳动教育/素质学分的快照读取用同一写法，未再单独在 release 上逐页点开核验。
