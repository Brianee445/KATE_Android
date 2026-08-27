// app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.dti.kate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dti.kate"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
        multiDexEnabled = true
        buildConfigField("String", "BACKEND_URL", "\"https://kate-backend-8aes.onrender.com/\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Single universal APK containing both ABIs, rather than splitting into
    // three artifacts (arm64-v8a-only, armeabi-v7a-only, universal) per
    // build. This app is side-loaded directly, not distributed through
    // Play Store's dynamic delivery, so per-ABI splits only add build time
    // and confusion about which file to download - the universal APK is
    // the only one actually used.
    splits {
        abi {
            isEnable = false
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "kate.jks")
            storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/attach_hotspot_windows.dll"
            excludes += "META-INF/licenses/**"
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
        }
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += "**/libtensorflowlite_c.so"
            pickFirsts += "**/libc++_shared.so"
        }
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("com.google.android.material:material:1.12.0")

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    kapt(libs.hilt.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ==================== BILLING ====================
    implementation(libs.billing.ktx)
    implementation(libs.gson)

    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)

    // net.java.dev.jna and vosk-android dependencies removed with Vosk -
    // see KateSttEngine's class doc for why (offline STT froze on
    // low-RAM/Transsion hardware, and the ~130MB bundled model was most
    // of the APK's 175MB). No offline STT fallback exists anymore.
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

    // Piper TTS dependency intentionally removed - piper-plus-g2p-android
    // requires Kotlin 2.1.0+, and with Piper on hold (Google/Deepgram
    // covers STT+TTS needs for now - see KateSttEngine and KateTtsEngine's
    // platform-TTS fallback), keeping Kotlin at 2.0.21 and dropping this
    // dependency avoids that whole compatibility problem rather than
    // carrying it while unused. PiperTtsEngine.kt was deleted rather than
    // left in the tree, since without this dependency it wouldn't compile
    // at all - re-add both together if Piper comes back later.

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemui)
    implementation(libs.accompanist.navigation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ui)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.test.manifest)
}

kapt {
    correctErrorTypes = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
