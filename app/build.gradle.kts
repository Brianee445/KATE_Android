// app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt) // was: kotlin("kapt") — your catalog already
                                     // declares this alias pinned to the same
                                     // kotlin version (2.0.21), so use it directly
                                     // instead of the version-less shorthand.
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

        // ==================== NATIVE BUILD ====================
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17 -O2 -fno-rtti -fno-exceptions")
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

    // ==================== EXTERNAL NATIVE BUILD ====================
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // ==================== SIGNING CONFIGS ====================
    signingConfigs {
        create("debug") {
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

    // ==================== BUILD FEATURES ====================
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ==================== COMPOSE ====================
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    // ==================== COMPILE OPTIONS ====================
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

    // ==================== PACKAGING ====================
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
            pickFirsts += "**/libvosk.so"
            pickFirsts += "**/libtensorflowlite_c.so"
            pickFirsts += "**/libc++_shared.so"
        }
    }

    // ==================== BUNDLE CONFIG (AAB) ====================
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

    // ==================== BUILD TYPES ====================
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

    // ==================== SOURCE SETS ====================
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
            jniLibs.srcDirs(
                "src/main/cpp/third_party/vosk/lib",
                "src/main/cpp/third_party/tflite/lib"
            )
        }
    }

    // ==================== TESTING ====================
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// ==================== DEPENDENCIES ====================
dependencies {
    // ==================== ANDROID CORE ====================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.multidex:multidex:2.0.1")

    // ==================== COMPOSE ====================
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)

    // ==================== HILT ====================
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    kapt(libs.hilt.compiler)

    // ==================== COROUTINES ====================
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // ==================== ROOM ====================
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ==================== DATASTORE ====================
    implementation(libs.datastore.preferences)

    // ==================== SECURITY (Encrypted Preferences) ====================
    implementation(libs.security.crypto)

    // ==================== NETWORKING ====================
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // ==================== SUPABASE ====================
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)

    // ==================== ML / AI ====================
    implementation(libs.vosk.android)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)

    // ==================== IMAGE LOADING ====================
    implementation(libs.coil.compose)

    // ==================== ACCOMPANIST ====================
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemui)
    implementation(libs.accompanist.navigation)

    // ==================== TESTING ====================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ui)

    // ==================== DEBUG ====================
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.test.manifest)
}

// ==================== KAPT ====================
kapt {
    correctErrorTypes = true
}

// ==================== KSP ====================
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ==================== DOWNLOAD VOSK MODEL ====================
tasks.register<DownloadVoskModelTask>("downloadVoskModel") {
    modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    modelDir = file("src/main/assets/vosk-model")
}

tasks.named("preBuild") {
    dependsOn("downloadVoskModel")
}

// ==================== CUSTOM TASK ====================
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
        java.util.zip.ZipFile(zipFile).use { zip ->
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
