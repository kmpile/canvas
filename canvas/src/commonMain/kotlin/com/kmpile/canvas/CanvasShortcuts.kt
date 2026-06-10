package com.kmpile.canvas

/**
 * One binding in the canvas shortcuts table. The same structure feeds both the in-app cheat-sheet
 * ([display] + [label]) and the native system HUDs — `KeyboardShortcutInfo` on Android and (when
 * wired) `UIKeyCommand` on iOS. Pure data; no UI.
 */
data class CanvasShortcut(
    /** Stable id for cross-platform dispatch — one of [CanvasActions]. Carried verbatim across the
     *  native HUD boundary (iOS `UIKeyCommand` propertyList, Android), so it stays a `String`. */
    val id: String,
    val label: String,
    /** In-app cheat-sheet rendering, e.g. `⌘⇧H`. Includes gesture-only entries like `2×click`. */
    val display: String,
    /**
     * Single-character key for the native HUD bridges (Android `KeyboardShortcutInfo` takes
     * `Char`; iOS `UIKeyCommand.input` takes `String`). `null` for gesture-only entries (those
     * still appear in the in-app cheat-sheet but aren't keyboard-bindable).
     */
    val key: String?,
    val cmd: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

/**
 * Stable action ids — the single source of truth shared by [CanvasShortcuts], the keyboard handler,
 * and [CanvasController.dispatch], so those three lists can't drift apart. [Pan] and [ZoomToggle] are
 * gesture-only (no `dispatch` branch): pan is the modal Space-hold, zoom-toggle the double-click.
 */
object CanvasActions {
    const val Inspect = "inspect"
    const val Interact = "interact"
    const val Pan = "pan"
    const val Zoom100 = "zoom-100"
    const val ZoomFill = "zoom-fill"
    const val ZoomToggle = "zoom-toggle"
    const val Rotate = "rotate"
    const val DayNight = "day-night"
    const val Shortcuts = "shortcuts"
}

/**
 * The canonical canvas shortcut table — single source of truth shared by the in-app cheat-sheet and
 * the per-platform native HUD bridges. See the keyboard handler in [CanvasRoot] for collision-safety
 * rationale (modifier-heavy combos to avoid colliding with text input or content shortcuts).
 */
val CanvasShortcuts: List<CanvasShortcut> = listOf(
    CanvasShortcut(CanvasActions.Inspect,    "Inspect (hand)",  "⌘⇧H",     "H", cmd = true, shift = true),
    CanvasShortcut(CanvasActions.Interact,   "Interact",        "⌘⇧V",     "V", cmd = true, shift = true),
    CanvasShortcut(CanvasActions.Pan,        "Pan (hold)",      "Space",   " "),
    CanvasShortcut(CanvasActions.Zoom100,    "Zoom 100%",       "⇧0",      "0", shift = true),
    CanvasShortcut(CanvasActions.ZoomFill,   "Zoom to fill",    "⇧1",      "1", shift = true),
    CanvasShortcut(CanvasActions.ZoomToggle, "Fill ⇄ 100%",     "2×click", key = null),
    // ⌥ (not ⇧): Chrome reserves ⌘⇧R for hard-reload and eats it before the canvas sees it.
    CanvasShortcut(CanvasActions.Rotate,     "Rotate 90°",      "⌘⌥R",     "R", cmd = true, alt = true),
    // ⌥ (not ⇧): Chrome reserves ⌘⇧A (Search tabs) and ⌘⇧G (find previous) on macOS.
    CanvasShortcut(CanvasActions.DayNight,   "Day / night",     "⌘⌥A",     "A", cmd = true, alt = true),
    CanvasShortcut(CanvasActions.Shortcuts,  "Shortcuts",       "⌘⇧?",     "/", cmd = true, shift = true),
)
