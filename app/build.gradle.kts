plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    id("org.jetbrains.kotlin.kapt") version "1.9.23"
}

android {
    namespace   = "com.kate.assistant"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.kate.assistant"
        minSdk        = 29
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        externalNativeBuild {
            cmake {
                cppFlags("")
                // Pass Gradle's prefab output dir so CMake can find libtensorflowlite
                arguments("-DANDROID_STL=c_shared")
            }
        }
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Gradle extracts the .so from the TFLite AAR and makes it
    // available to CMake automatically via prefab
    buildFeatures {
        compose = true
        prefab  = true          // ← tells Gradle to expose AAR native libs to CMake
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
        }
    }

    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ── TFLite — Gradle downloads + extracts .so automatically ──
    implementation(libs.tflite)

    // ── Hilt ────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // ── Compose ─────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    // ── Coroutines ───────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Room ─────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // ── DataStore ────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Core ─────────────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
}
