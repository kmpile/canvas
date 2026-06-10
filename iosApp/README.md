# iOS host (Xcode setup)

The `:canvas` module exports an `Canvas.framework` (`CanvasViewController()` /
`createCanvasHost()`). `CanvasApp.swift` is the thin SwiftUI app that embeds it. There is no
committed `.xcodeproj` — create one once, locally, on a Mac:

1. **Build the framework:**
   ```bash
   ./gradlew :canvas:linkDebugFrameworkIosSimulatorArm64
   ```
   Output: `canvas/build/bin/iosSimulatorArm64/debugFramework/Canvas.framework`.

2. **New Xcode project** → iOS App, SwiftUI lifecycle. Delete its generated `ContentView.swift`
   and `App.swift`; add `CanvasApp.swift` from this folder.

3. **Embed the framework:** add a *Run Script* build phase (before "Compile Sources") that runs
   ```bash
   cd "$SRCROOT/.." && ./gradlew :canvas:embedAndSignAppleFrameworkForXcode
   ```
   and set the env vars Xcode passes (`CONFIGURATION`, `SDK_NAME`, `ARCHS`, …) — the
   `embedAndSignAppleFrameworkForXcode` task reads them. Add `$(SRCROOT)/../canvas/build/xcode-frameworks/...`
   to **Framework Search Paths**, and link `Canvas.framework`.

4. Pick a **concrete arm64 simulator** (not "Any iOS Simulator") and run.

For a device build, declare both `iosArm64()` and `iosSimulatorArm64()` (already done in
`canvas/build.gradle.kts`) and set your `DEVELOPMENT_TEAM` for automatic signing.

> Tip: the simplest route is to copy the project structure from any Compose-Multiplatform iOS
> sample (e.g. the KMP wizard output) and point its framework-embed script at `:canvas`.
