# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Remove debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Compose rules - Enhanced
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.runtime.snapshots.** { *; }
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateList {
    boolean conditionalUpdate(boolean, kotlin.jvm.functions.Function1);
    boolean conditionalUpdate$default(androidx.compose.runtime.snapshots.SnapshotStateList, boolean, kotlin.jvm.functions.Function1, int, java.lang.Object);
    java.lang.Object mutate(kotlin.jvm.functions.Function1);
    void update(boolean, kotlin.jvm.functions.Function1);
    void update$default(androidx.compose.runtime.snapshots.SnapshotStateList, boolean, kotlin.jvm.functions.Function1, int, java.lang.Object);
}

# Prevent optimization of Compose state objects
-keep class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.State { *; }
-keep class androidx.compose.runtime.snapshots.* { *; }
-keepclassmembers class androidx.compose.runtime.snapshots.* { *; }

# Material 3
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Koin dependency injection
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ObjectBox
-keep class io.objectbox.** { *; }
-keepclassmembers class ** {
    @io.objectbox.annotation.* *;
}

# Native libraries
-keep class **.R$* { *; }
-keep class **.BuildConfig { *; }

# Keep data classes
-keep class io.orabel.orabelandroid.data.** { *; }

# STT and TTS classes
-keep class io.orabel.orabelandroid.stt.** { *; }
-keep class io.orabel.orabelandroid.tts.** { *; }
-keep class io.orabel.orabelandroid.translation.** { *; }

# Speech Recognition and TTS
-keep class android.speech.** { *; }
-keep class android.speech.tts.** { *; }

# Motor TTS Sherpa ONNX
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.k2fsa.sherpa.onnx.tts.engine.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ML Kit Translation
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Performance optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Additional performance optimizations
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Aggressive optimizations for better performance
-repackageclasses ''
-dontobfuscate

# Fix for APK loading issues
-keep class androidx.startup.** { *; }
-keep class androidx.multidex.** { *; }
-overloadaggressively
-repackageclasses ''
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Memory optimization
-dontshrink
-dontpreverify

# System UI and Toast protection - FIX FOR APK LOADING ERRORS
-keep class android.widget.Toast { *; }
-keep class com.android.systemui.** { *; }
-dontwarn com.android.systemui.**

# Exception handling
-keep class io.orabel.orabelandroid.utils.GlobalExceptionHandler { *; }
-keep class io.orabel.orabelandroid.utils.SafeToast { *; }

# Resource protection
-keep class android.content.res.** { *; }
-keep class android.app.ResourcesManager { *; }
-dontwarn android.content.res.**

# APK assets protection  
-keep class android.content.res.ApkAssets { *; }
-dontwarn android.content.res.ApkAssets

# Prevent optimization of error handling
-keep class * extends java.lang.Throwable { *; }
-keep class java.lang.Thread$UncaughtExceptionHandler { *; }

# Toast related classes to prevent SystemUIToast errors
-keep class android.widget.** { *; }
-keep class android.app.ApplicationPackageManager { *; }
-keep class android.content.pm.PackageItemInfo { *; }
-dontwarn android.widget.**