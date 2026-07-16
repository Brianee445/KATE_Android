// settings.gradle.kts

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    
    // Plugin resolution strategy
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.google.dagger.hilt")) {
                useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    
    repositories {
        google()
        mavenCentral()
        
        // JitPack for GitHub-hosted libraries
        maven("https://jitpack.io")
        
        // Sonatype Snapshots for pre-release versions
        maven("https://oss.sonatype.org/content/repositories/snapshots") {
            mavenContent { snapshotsOnly() }
        }
    }
    
    // NOTE: no explicit versionCatalogs{} block needed here. Gradle 7.4+
    // auto-detects gradle/libs.versions.toml and registers it as the "libs"
    // catalog by itself. Explicitly calling from(files(...)) on top of that
    // is a *second* from() call on the same catalog, which Gradle 8.6
    // rejects with "you can only call the 'from' method a single time".
}

rootProject.name = "Kate"
include(":app")

// NOTE: enableFeaturePreview("VERSION_CATALOGS") and
// enableFeaturePreview("STABLE_CONFIGURATION_CACHE") were removed.
// Both features stabilized years ago (Gradle 7.x) and are no longer valid
// preview-feature names on Gradle 8.6 — calling either throws
// "There is no feature named ..." and fails the build.
// Version catalogs work automatically from gradle/libs.versions.toml above,
// and configuration cache is already enabled via
// org.gradle.configuration-cache=true in gradle.properties.
