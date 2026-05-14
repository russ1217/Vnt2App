# Flutter Wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Rust FFI
-keep class com.flutter_rust_bridge.** { *; }

# VNT
-keep class top.wherewego.vnt2_app.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Play Core Library (Flutter需要)
-dontwarn com.google.android.play.core.**
-keep class com.google.android.play.core.** { *; }
