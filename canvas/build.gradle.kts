@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The standalone, multiplatform "Canvas" harness — a single module that hosts the component canvas
// (CanvasRoot) as its own app on desktop (hot reload), web (wasm), and iOS (the Canvas framework),
// and as a reusable library the thin :androidApp launcher depends on.
//
// Android is the one exception to "the module is also an app": AGP 9 forbids
// `com.android.application` in a KMP module (the KMP Android plugin is library-only), so this
// module's Android target is a *library* and the thin :androidApp (CanvasActivity + manifest)
// depends on it for the Android launcher app.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.hot.reload)
}

// Maven coordinates — consumers depend on `com.kmpile:canvas`. A Gradle composite build
// (`includeBuild`) substitutes that module identity with this project automatically, so a consuming
// app builds the harness from source and keeps Compose hot reload across the boundary.
group = "com.kmpile"
version = "0.1.0"

val jvm = libs.versions.jvmTarget.get()

kotlin {
    jvmToolchain(jvm.toInt())

    android {
        namespace = "com.kmpile.canvas"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        androidResources.enable = true
    }

    // Own iOS framework (`Canvas`). A Swift app embeds it and calls `createCanvasHost()`. iOS targets
    // only build on macOS hosts; CANVAS_SKIP_IOS=1 drops them so other hosts (e.g. Windows/Linux CI)
    // can configure + build the JVM/web/Android targets without the Apple toolchain.
    if (System.getenv("CANVAS_SKIP_IOS") != "1") {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework { baseName = "Canvas" }
        }
    }

    // Desktop + web are real *apps* of this module (the canvas hosts), not just library targets: the
    // wasm target needs an executable binary; the jvm app is launched via compose.desktop below.
    wasmJs {
        outputModuleName = "canvas"
        browser {
            commonWebpackConfig { outputFileName = "canvas.js" }
        }
        binaries.executable()
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(JvmTarget.fromTarget(jvm)) }
}

// Desktop launcher (raw `./gradlew :canvas:run`) — the hot-reload entry is hotRun below.
compose.desktop {
    application { mainClass = "com.kmpile.canvas.DesktopMainKt" }
}

// Desktop host for the canvas — hot-reloadable: `./gradlew :canvas:hotRun --autoReload`
tasks.register<ComposeHotRun>("hotRun") {
    mainClass.set("com.kmpile.canvas.DesktopMainKt")
    // Desktop trackpad pinch reaches the JBR-only Apple gesture API via reflection (see DesktopMain.kt).
    jvmArgs("--add-exports", "java.desktop/com.apple.eawt.event=ALL-UNNAMED")
}

// The concrete composable being previewed lives in a gitignored CanvasContent.kt so scratch edits
// never get committed. Seed it from the committed template on first build (so fresh clones compile);
// once it exists, the dev's local edits persist (task is skipped).
val seedCanvasContent by tasks.registering {
    val template = layout.projectDirectory.file("canvas-content.template").asFile
    val target = layout.projectDirectory
        .file("src/commonMain/kotlin/com/kmpile/canvas/CanvasContent.kt").asFile
    onlyIf { !target.exists() }
    doLast { template.copyTo(target) }
}
tasks.withType<KotlinCompilationTask<*>>().configureEach { dependsOn(seedCanvasContent) }
