-keep class com.kate.assistant.bridge.** { *; }
-keepclassmembers class com.kate.assistant.bridge.KateBridge {
    public void onNativeEvent(java.lang.String, java.lang.String);
}
