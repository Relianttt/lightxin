# ProGuard rules for LightXin

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson
-keep class com.lightxin.**.data.** { *; }
-keep class com.lightxin.**.domain.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# RSA keys
-keep class com.lightxin.core.auth.RSAUtils { *; }

# CameraX — release 包扫码必须保留，否则 ImageProxy/PreviewView 被优化后识别失效
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# ML Kit Barcode Scanning — 必须 keep 整个 com.google.mlkit 树，因为内部依赖注入
# 在 MlKitInitProvider 启动时解析组件，混淆后接口名/类名不匹配会导致直接崩溃
-keep class com.google.mlkit.** { *; }
