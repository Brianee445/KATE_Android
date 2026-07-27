// app/build.gradle.kts

import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipFile

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

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17 -O2 -fno-rtti -fno-exceptions -Wl,-z,max-page-size=16384")
                arguments(
                    "-DANDROID_PLATFORM=android-24",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DANDROID_STL=c++_shared"
                )
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Produces one installable APK per ABI (plus a universal fallback)
    // from a single `./gradlew assembleRelease` run - so both 32-bit and
    // 64-bit devices can be tested directly without relying on Play
    // Store's dynamic delivery.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
            jniLibs.srcDirs(
                "src/main/cpp/third_party/tflite/lib",
                // kate_engine.so dynamically links against libvosk.so (passed
                // straight to the linker in CMakeLists.txt, not statically
                // embedded) - without packaging it here too, the APK ships
                // kate_engine.so with an unresolved DT_NEEDED dependency and
                // System.loadLibrary("kate_engine") fails at runtime even
                // though the build compiles and links cleanly.
                "src/main/cpp/third_party/vosk/lib"
            )
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
    implementation(libs.gson)

    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)

    // vosk-android / JNA removed: VoskManager now talks to Kate's own
    // native engine (kate_engine.so via NativeBridge) instead of the
    // third-party org.vosk JNA bindings. See VoskManager.kt for why.
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

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

tasks.register<DownloadVoskModelTask>("downloadVoskModel") {
    modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    modelDir = file("src/main/assets/vosk-model")
}

tasks.named("preBuild") {
    dependsOn("downloadVoskModel")
}

abstract class DownloadVoskModelTask : DefaultTask() {
    @get:Input
    abstract val modelUrl: Property<String>

    @get:OutputDirectory
    abstract val modelDir: DirectoryProperty

    @TaskAction
    fun downloadAndExtract() {
        val destDir = modelDir.get().asFile
        if (destDir.exists() && destDir.listFiles()?.isNotEmpty() == true) {
            println("✅ Vosk model already exists, skipping download")
            return
        }

        println("📥 Downloading Vosk model from ${modelUrl.get()}")

        val zipFile = File(destDir.parentFile, "vosk-model.zip")
        URL(modelUrl.get()).openStream().use { input ->
            FileOutputStream(zipFile).use { output ->
                input.copyTo(output)
            }
        }

        println("📦 Extracting Vosk model...")
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    val targetFile = File(destDir, entry.name)
                    targetFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        zipFile.delete()

        val modelFile = File(destDir, "am/final.mdl")
        if (modelFile.exists()) {
            println("✅ Vosk model downloaded successfully (${modelFile.length() / 1024 / 1024} MB)")
        } else {
            throw GradleException("❌ Vosk model download failed - model file not found")
        }
    }
}
