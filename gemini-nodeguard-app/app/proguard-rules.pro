# ==============================================================================
# GEMINI NODEGUARD v1.0.0-release — R8 / PROGUARD PRODUCTION HARDENING MANIFEST
# ==============================================================================

# 1. Preserve SourceFile & LineNumberTable for DiagnosticErrorCollector boundary stacktraces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute NodeGuardSource

# 2. Native Go (Sing-box libbox.so) & Rust (Xray-core) JNI Bridge Keep Rules
-keep class com.example.bridge.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# 3. Serialization, Proxy URI Parser & Dynamic GitHub Actions Remote-Config Models
-keep class com.example.model.** { *; }
-keep class com.example.parser.** { *; }
-keep class com.example.security.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# 4. OkHttp3, Okio & Conscrypt / TLS 1.3 ALPN Reflection Rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# 5. Zero-Trust Production Log Stripping (Strip verbose debug/trace logs at bytecode level)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
