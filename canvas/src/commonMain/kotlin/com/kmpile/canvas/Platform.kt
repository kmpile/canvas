package com.kmpile.canvas

/**
 * True on platforms with a physical keyboard (desktop, web), where the canvas binds keyboard
 * shortcuts and offers the long-press shortcuts cheat-sheet. False on touch-only platforms
 * (Android, iOS), which drive the canvas entirely through the pill + gestures.
 */
internal expect val SupportsKeyboardShortcuts: Boolean

/**
 * True on platforms that surface keyboard shortcuts through a **native system HUD** (Android's
 * Keyboard Shortcuts Helper via `onProvideKeyboardShortcuts`; iOS's Discoverability HUD via
 * `UIKeyCommand`). On those platforms the in-app cheat-sheet card is redundant and we
 * suppress it. Desktop + web have no native HUD, so the cheat-sheet card is kept as the only path.
 */
internal expect val HasNativeShortcutsHud: Boolean

/**
 * True on platforms where the OS rotates the app viewport with the device (iOS / Android). The
 * canvas keeps `LocalCanvasOrientation` in sync with viewport aspect so a previewed `FramePreview`
 * reflows on physical rotation, no hotkey needed. False on desktop / web where viewport aspect is
 * the window's, not the user's intent — orientation is driven solely by the rotate hotkey there.
 */
internal expect val OrientationFollowsDevice: Boolean

/**
 * Magnitude of a pointer `scrollDelta.y` for **one mouse-wheel notch** on this platform — used to
 * normalize Ctrl+scroll zoom to a consistent per-notch step regardless of how the platform scales
 * wheel deltas. Desktop JVM reports a normalized `±1` per notch; the browser passes the raw DOM
 * `deltaY` (`±120`). A fractional delta (a trackpad's fine scroll) yields a fractional notch, so
 * zoom stays smooth and proportional. Not on a hot path for touch platforms (they zoom via pinch).
 */
internal expect val ScrollZoomUnitsPerNotch: Float
