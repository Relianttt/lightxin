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
