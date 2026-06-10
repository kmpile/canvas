package com.kmpile.canvas

// We can't detect GCKeyboard cheaply without a runtime check; the SwiftUI wrapper registers
// UIKeyCommands unconditionally so iPad users with a BT/USB keyboard get the Discoverability HUD.
// Compose's own onKeyEvent fallback is still used (e.g. for Space-hold pan), so leaving this true
// is harmless when no keyboard is attached.
internal actual val SupportsKeyboardShortcuts: Boolean = true

// CanvasShortcutsHostController in CanvasApp.swift surfaces the shortcuts in iOS's
// Discoverability HUD (hold ⌘ to reveal) via UIKeyCommand → dispatch into Compose state.
internal actual val HasNativeShortcutsHud: Boolean = true

// iOS viewport reflows on device rotation — sync the canvas orientation to that.
internal actual val OrientationFollowsDevice: Boolean = true

// iOS has no scroll-wheel device — zoom is always pinch — so this value is effectively unused.
internal actual val ScrollZoomUnitsPerNotch: Float = 120f
