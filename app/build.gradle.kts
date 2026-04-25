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
            cmake { cppFlags("") }
        }
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    externalNativeBuild {
        cmake {
            path    = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            storeFile      = file(System.getenv("KEYSTORE_PATH") ?: "kate.jks")
            storePassword  = System.getenv("KEY_STORE_PASSWORD") ?: ""
            keyAlias       = System.getenv("KEY_ALIAS") ?: ""
            keyPassword    = System.getenv("KEY_PASSWORD") ?: ""
            enableV1Signing = false   // V1 not needed on API 29+
            enableV2Signing = true    // Required
            enableV3Signing = true    // Android 9+ preferred
            enableV4Signing = true    // Incremental ADB installs
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = false
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable  = true
            // Force V2/V3 on debug too so sideloading works on Android 14
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.13" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.tflite)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
}
