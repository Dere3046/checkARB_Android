# JNI interfaces
-keep class com.dere3046.arbinspector.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ViewModel
-keep class com.dere3046.checkarb.MainViewModel
-keepclassmembers class com.dere3046.checkarb.MainViewModel {
    <init>(...);
}

# Data classes
-keep class com.dere3046.arbinspector.ArbResult { *; }
-keepclassmembers class com.dere3046.arbinspector.ArbResult {
    <init>();
    public int major;
    public int minor;
    public int arb;
    public java.util.List debugMessages;
    public java.lang.String error;
}

# Keep Kotlin Metadata
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.annotation.Annotation
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}

# Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# libsu
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# DataStore
-keep class androidx.datastore.** { *; }
-keep class com.google.protobuf.** { *; }

# Navigation
-keep class androidx.navigation.** { *; }

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep annotations
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
