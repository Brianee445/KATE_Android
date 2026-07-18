// build.gradle.kts (Project Level)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// ============================================================================
// REPOSITORIES ARE DEFINED IN settings.gradle.kts
// DO NOT ADD THEM HERE – IT WILL CAUSE A BUILD FAILURE
// ============================================================================

// Remove the allprojects block with repositories – it's now in settings.gradle.kts

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
