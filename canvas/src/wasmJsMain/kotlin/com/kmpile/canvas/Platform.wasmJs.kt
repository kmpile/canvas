package com.kmpile.canvas

internal actual val SupportsKeyboardShortcuts: Boolean = true

// Browsers don't surface app shortcuts in any native HUD — the in-app cheat-sheet card is the only path.
internal actual val HasNativeShortcutsHud: Boolean = false

// Browser viewport changes only via user window resize — not the user's "rotation" intent — so
// keep orientation driven by the rotate hotkey only, matching desktop.
internal actual val OrientationFollowsDevice: Boolean = false

// Compose-web passes the raw DOM WheelEvent.deltaY through as scrollDelta.y — ~±120 per notch
// (measured: deltaY -120 → one notch). Matches the browser's own Ctrl+wheel zoom granularity.
internal actual val ScrollZoomUnitsPerNotch: Float = 120f
