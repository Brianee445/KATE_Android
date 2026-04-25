# Keep Kate JNI bridge — C calls back into this
-keep class com.kate.assistant.bridge.** { *; }
-keepclassmembers class com.kate.assistant.bridge.KateBridge {
    public void onNativeEvent(java.lang.String, java.lang.String);
}

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Room entities
-keep class com.kate.assistant.data.db.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep TFLite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
