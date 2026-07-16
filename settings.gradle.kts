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
    
    // Version catalogs are automatically loaded from gradle/libs.versions.toml
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "Kate"
include(":app")

// Enable preview features
enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
