package com.kmpile.canvas

internal actual val SupportsKeyboardShortcuts: Boolean = true

// Desktop JVM has no native shortcut HUD — the in-app cheat-sheet card is the only path.
internal actual val HasNativeShortcutsHud: Boolean = false

// Desktop window aspect isn't user intent — keep orientation driven by the rotate hotkey only.
internal actual val OrientationFollowsDevice: Boolean = false

// Compose desktop normalizes mouse-wheel scroll to ±1.0 per notch (measured).
internal actual val ScrollZoomUnitsPerNotch: Float = 1f
