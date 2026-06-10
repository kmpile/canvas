package com.kmpile.canvas

import android.view.InputDevice

/**
 * Custom getter so a Bluetooth/USB keyboard plugged in mid-session is picked up on next access (e.g.
 * when ControlPill recomposes). Enumerates the attached input devices via the Context-free static
 * [InputDevice] API (more reliable than `Resources.getSystem().configuration.keyboard`, whose system
 * Configuration isn't guaranteed to carry an app-accurate value): a non-virtual device reporting an
 * alphabetic keyboard means a real keyboard is attached.
 */
internal actual val SupportsKeyboardShortcuts: Boolean
    get() = InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.let { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC } == true
    }

// CanvasActivity.onProvideKeyboardShortcuts feeds Android's system Keyboard Shortcuts Helper.
internal actual val HasNativeShortcutsHud: Boolean = true

// Android viewport reflows on device rotation — sync the canvas orientation to that.
internal actual val OrientationFollowsDevice: Boolean = true

// Touch zoom on Android is pinch (detectTransformGestures), so Ctrl+scroll is only reachable with
// an external mouse — a rare edge. Assume a pixel-scale wheel delta like the browser; not verified.
internal actual val ScrollZoomUnitsPerNotch: Float = 120f
