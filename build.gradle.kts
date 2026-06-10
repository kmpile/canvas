plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.hot.reload) apply false
}

allprojects {
    // CMP 1.12.0-alpha01 transitively pulls the legacy JetBrains lifecycle fork
    // (org.jetbrains.androidx.lifecycle) alongside Google's androidx.lifecycle. Both ship the same
    // classes: on desktop the duplicate ViewModelStoreOwner lookups recurse into each other
    // (StackOverflowError at launch). Google's androidx.lifecycle is multiplatform now, so drop the
    // fork everywhere and keep a single lifecycle on every target's classpath.
    configurations.all {
        exclude(group = "org.jetbrains.androidx.lifecycle")
    }
}
