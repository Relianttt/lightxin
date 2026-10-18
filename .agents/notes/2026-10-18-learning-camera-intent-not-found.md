---
doc_type: learning
track: pitfall
status: active
date: 2026-10-18
tags: [android, camera, manifest, ime]
---

## 现象

在 Pixel 4 / Android 13 上点请假或报修的“拍照”，进程直接退出。`dropbox` 记录：

```
android.content.ActivityNotFoundException: No Activity found to handle Intent {
  act=android.media.action.IMAGE_CAPTURE ... (has extras) }
  at ...RepairCreateScreenKt.RepairCreateScreen$lambda$16$lambda$15(RepairCreateScreen.kt:112)
```

系统里 `com.google.android.GoogleCamera` 存在，`CaptureActivity` 的 `IMAGE_CAPTURE` alias 存在且 `exported=true`；显式 `am start -n com.google.android.GoogleCamera/com.android.camera.activity.CaptureActivity` 能打开相机。隐式 `am start -a android.media.action.IMAGE_CAPTURE`、`cmd package resolve-activity` 与 App 内 `ActivityResultContracts.TakePicture` 都解析不到。

## 根因

未确认。已排除：

- 硬件与权限：`pm list features` 有 camera/any/front/flash，Manifest 有 `CAMERA`，权限请求正常。
- 相机 App 缺失/禁用：包存在且 `enabled=0`、`stopped=false`，显式启动成功。

尝试过但**未解决**：

- 在 Manifest 增加 `<queries><intent><action android:name="android.media.action.IMAGE_CAPTURE" /></intent></queries>`（targetSdk ≥ 30 的包可见性要求），重新安装后仍解析不到。

当前判断是系统/相机 App 的 activity-alias 解析异常，或该 ROM 对 `IMAGE_CAPTURE` 的额外过滤；需要进一步看 `PackageManagerService` 解析日志，暂不把原因写成结论。

## 解法

- 代码侧已加兜底：`cameraLauncher.launch(uri)` / 权限 launcher 用 `runCatching` 包住，`ActivityNotFoundException` 时 Snackbar 提示“没有可用的相机应用，请从相册选择”，不再闪退。
- 相册路径 `PickVisualMedia` 正常，可作为替代入口。
- 真正修复需要改**应用内相机**（CameraX），不再依赖外部 `IMAGE_CAPTURE`；这会新增依赖、预览/拍照 UI 与文件输出，属于独立改动。

## 预防

- 外部隐式 Intent（拍照、选文件、拨号）在 targetSdk ≥ 30 下先确认 `<queries>` 与目标设备解析结果；不要只测模拟器。
- 对 `ActivityResultContracts.TakePicture` 这类外部 Activity 启动，必须捕获 `ActivityNotFoundException`，否则用户点一下就是闪退。
- 若相机是核心路径，优先 CameraX 应用内拍摄，而不是依赖厂商相机 App。
