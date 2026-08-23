# ============================================================================
# KATE ASSISTANT - PROGUARD RULES
# ============================================================================
# These rules prevent code stripping/obfuscation that would break:
# - JNI native calls (C++ -> Kotlin)
# - Reflection (Hilt, Room, Retrofit, Gson)
# - Runtime annotation processing
# ============================================================================

# ============================================================================
# 1. KATE JNI BRIDGE
# ============================================================================
# Kate C++ engine calls back into Kotlin via JNI
# Keep the bridge class and all its native methods

-keep class com.dti.kate.core.NativeBridge { *; }
-keepclassmembers class com.dti.kate.core.NativeBridge {
    public void onTranscription(java.lang.String, boolean);
    public void onResponse(java.lang.String);
    public void onError(java.lang.String);
    public void onStateChange(int);
}

-keep class com.dti.kate.core.NativeBridge$Callbacks { *; }

# Keep all native methods (any class)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI method signatures
-keepclassmembers class * {
    private static native *** native*(...);
}

# Keep C++ library names
-keep class com.dti.kate.** {
    static final java.lang.String LIBRARY_NAME;
}


# ============================================================================
# 3. TENSORFLOW LITE
# ============================================================================
# TFLite uses reflection for delegate loading

-keep class org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** {
    *;
}
-dontwarn org.tensorflow.**

-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** {
    *;
}
-dontwarn org.tensorflow.lite.**

# Keep TFLite delegates
-keep class org.tensorflow.lite.nnapi.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.xnnpack.** { *; }


# ============================================================================
# 4. HILT (Dependency Injection)
# ============================================================================
# Hilt uses reflection at runtime for DI

-keep class dagger.hilt.** { *; }
-keepclassmembers class dagger.hilt.** {
    *;
}
-keep class javax.inject.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.internal.** { *; }
-keep class dagger.hilt.android.** { *; }
-keep class dagger.hilt.android.components.** { *; }

# Keep Hilt entry points
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Keep Hilt ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * implements dagger.hilt.android.lifecycle.HiltViewModelFactory { *; }


# ============================================================================
# 5. ROOM (Database)
# ============================================================================
# Room uses reflection for query generation

-keep class androidx.room.** { *; }
-keepclassmembers class androidx.room.** {
    *;
}

# Keep entity classes
-keep class com.dti.kate.data.local.** { *; }
-keepclassmembers class com.dti.kate.data.local.** {
    <init>(...);
    public static *** create(...);
}

# Keep DAOs
-keep class com.dti.kate.data.dao.** { *; }
-keepclassmembers class com.dti.kate.data.dao.** {
    <init>(...);
}

# Keep database classes
-keep class com.dti.kate.data.** { *; }

# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.DatabaseConfiguration { *; }


# ============================================================================
# 6. RETROFIT / OKHTTP
# ============================================================================
# Retrofit uses reflection for interface generation

-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** {
    *;
}
-dontwarn retrofit2.**

-keep class com.squareup.okhttp3.** { *; }
-keepclassmembers class com.squareup.okhttp3.** {
    *;
}
-dontwarn com.squareup.okhttp3.**

-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** {
    *;
}
-dontwarn okhttp3.**

# Keep API service interfaces
-keep interface com.dti.kate.network.** { *; }
-keepclassmembers interface com.dti.kate.network.** {
    *;
}


# ============================================================================
# 7. GSON
# ============================================================================
# Gson uses reflection for serialization/deserialization

-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** {
    *;
}
-dontwarn com.google.gson.**

# Keep model classes (all)
-keep class com.dti.kate.network.models.** { *; }
-keepclassmembers class com.dti.kate.network.models.** {
    <init>(...);
    <fields>;
}
-keep class com.dti.kate.data.models.** { *; }
-keepclassmembers class com.dti.kate.data.models.** {
    <init>(...);
    <fields>;
}


# ============================================================================
# 8. SUPABASE
# ============================================================================
# Supabase uses reflection for serialization

-keep class io.github.jan.** { *; }
-keepclassmembers class io.github.jan.** {
    *;
}
-dontwarn io.github.jan.**

-keep class com.github.jan.** { *; }
-keepclassmembers class com.github.jan.** {
    *;
}
-dontwarn com.github.jan.**

# Keep Supabase generated code
-keep class * implements io.github.jan.supabase.annotations.** { *; }


# ============================================================================
# 9. JETPACK COMPOSE
# ============================================================================
# Compose uses runtime reflection for previews and animations

-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** {
    *;
}
-dontwarn androidx.compose.**

# Keep Compose UI
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }

# Keep Compose previews (for development)
-keep class * extends androidx.compose.ui.tooling.preview.Preview { *; }

# Keep Compose navigation
-keep class androidx.navigation.** { *; }


# ============================================================================
# 10. COIL (Image Loading)
# ============================================================================
# Coil uses reflection for image loading

-keep class coil.** { *; }
-keepclassmembers class coil.** {
    *;
}
-dontwarn coil.**


# ============================================================================
# 11. ACCOMPANIST
# ============================================================================
# Accompanist libraries

-keep class com.google.accompanist.** { *; }
-keepclassmembers class com.google.accompanist.** {
    *;
}
-dontwarn com.google.accompanist.**


# ============================================================================
# 12. COROUTINES
# ============================================================================
# Coroutines use reflection for suspend functions

-keep class kotlin.coroutines.** { *; }
-keepclassmembers class kotlin.coroutines.** {
    *;
}
-dontwarn kotlin.coroutines.**

-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    *;
}
-dontwarn kotlinx.coroutines.**

# Keep coroutine exception handlers
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }


# ============================================================================
# 13. DATASTORE
# ============================================================================
# DataStore uses reflection for preferences

-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** {
    *;
}
-dontwarn androidx.datastore.**

-keep class androidx.datastore.preferences.** { *; }
-keepclassmembers class androidx.datastore.preferences.** {
    *;
}


# ============================================================================
# 14. SECURITY CRYPTO
# ============================================================================

-keep class androidx.security.** { *; }
-keepclassmembers class androidx.security.** {
    *;
}
-dontwarn androidx.security.**


# ============================================================================
# 15. ANDROIDX / SUPPORT LIBRARIES
# ============================================================================

-keep class androidx.** { *; }
-keepclassmembers class androidx.** {
    *;
}
-dontwarn androidx.**

-keep class android.support.v4.** { *; }
-keepclassmembers class android.support.v4.** {
    *;
}
-dontwarn android.support.v4.**


# ============================================================================
# 16. GENERIC KOTLIN
# ============================================================================
# Keep Kotlin metadata and reflection

-keep class kotlin.** { *; }
-keepclassmembers class kotlin.** {
    *;
}
-dontwarn kotlin.**

-keep class kotlinx.** { *; }
-keepclassmembers class kotlinx.** {
    *;
}
-dontwarn kotlinx.**

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Kotlin reflection (if used)
-keep class kotlin.reflect.** { *; }


# ============================================================================
# 17. ANNOTATIONS
# ============================================================================
# Keep annotations for runtime reflection

-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile
-keepattributes LineNumberTable


# ============================================================================
# 18. SERIALIZATION / DESERIALIZATION
# ============================================================================
# Keep serialization constructors

-keepclassmembers class * {
    *** INSTANCE;
    *** Companion;
}

-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


# ============================================================================
# 19. EXCEPTION HANDLING
# ============================================================================
# Keep custom exceptions

-keep class com.dti.kate.core.exceptions.** { *; }
-keepclassmembers class com.dti.kate.core.exceptions.** {
    *;
}


# ============================================================================
# 20. CRASH REPORTING
# ============================================================================
# Keep crash reporting classes (if using Sentry, Firebase, etc.)

# Sentry
-keep class io.sentry.** { *; }
-keepclassmembers class io.sentry.** {
    *;
}
-dontwarn io.sentry.**

# Firebase
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** {
    *;
}
-dontwarn com.google.firebase.**


# ============================================================================
# 21. LOGGING (Remove in release)
# ============================================================================
# Remove all Log calls in release (optional optimization)

#-assumenosideeffects class android.util.Log {
#    public static *** d(...);
#    public static *** v(...);
#    public static *** i(...);
#    public static *** w(...);
#    public static *** e(...);
#}


# ============================================================================
# 22. OBFUSCATION EXCEPTIONS
# ============================================================================
# Don't obfuscate Kate package (easier debugging)

-keep class com.dti.kate.** { *; }
-keepclassmembers class com.dti.kate.** {
    *;
}

# Don't obfuscate C++ bridge
-keep class com.dti.kate.core.** { *; }
-keepclassmembers class com.dti.kate.core.** {
    public *;
}


# ============================================================================
# 23. WARNINGS SUPPRESSION
# ============================================================================

# Suppress warnings for missing classes (libraries we don't use)
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.apache.**
-dontwarn java.awt.**

# Suppress TFLite warnings
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.flatbuffers.**

# Suppress Compose warnings
-dontwarn androidx.compose.ui.**

