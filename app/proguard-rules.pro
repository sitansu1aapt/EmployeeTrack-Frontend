# Keep CameraX and ZXing classes used via reflection to avoid stripping in release
-keep class androidx.camera.** { *; }
-keep class com.google.zxing.** { *; }
-dontwarn androidx.camera.**
-dontwarn com.google.zxing.**

# Keep Kotlin serialization generated classes
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.SerialName *; }
-keep class kotlinx.serialization.** { *; }

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Additional ProGuard rules for Iqoo Z7s 5G device compatibility

# Keep patrol-related classes and their members
-keep class com.yatri.patrol.** { *; }
-keep class com.yatri.ActivePatrolActivity { *; }
-keep class com.yatri.ActivePatrolActivity$QRCodeAnalyzer { *; }
-keep class com.yatri.ActivePatrolActivity$QrCheckpoint { *; }

# Keep image analysis and camera related classes
-keep class androidx.camera.core.ImageAnalysis$Analyzer { *; }
-keep class androidx.camera.core.ImageProxy { *; }
-keep class androidx.camera.lifecycle.ProcessCameraProvider { *; }

# Keep location services
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.maps.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }

# Keep Retrofit and OkHttp classes
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Keep data classes used in API calls
-keep class com.yatri.patrol.ScanCheckpointPayload { *; }
-keep class com.yatri.patrol.PatrolStatusResponse { *; }
-keep class com.yatri.patrol.PatrolSession { *; }

# Keep enum classes
-keepclassmembers enum * { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Samsung device specific workarounds
-keep class com.samsung.** { *; }
-dontwarn com.samsung.**

# Iqoo/Vivo device specific workarounds
-keep class com.vivo.** { *; }
-dontwarn com.vivo.**

# Keep all classes with @Serializable annotation
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep all reflection-based classes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable