plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Thin Android launcher for the multiplatform component canvas (the `:canvas` module). On AGP 9 a
// KMP module can't be a `com.android.application` (the Android target of `:canvas` is therefore a
// library), so this tiny plain-Android app — just CanvasActivity + manifest — is the Android face of
// the canvas. AGP 9's built-in Kotlin compiles the source, so no separate kotlin-android plugin.
android {
    namespace = "com.kmpile.canvas.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kmpile.canvas"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("app.versionCode").get().toInt()
        versionName = providers.gradleProperty("app.versionName").get()
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":canvas"))
    implementation(libs.androidx.activity.compose)
    // The :canvas library exposes Compose via `implementation`, so the launcher pulls its own
    // compose deps to compile `setContent { CanvasRoot() }`.
    implementation(libs.compose.material3)
}
