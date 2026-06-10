package com.kmpile.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS host entry for the canvas, exported in the `Canvas` framework. A Swift app/scheme hosts this
 * view controller (see iosApp/README.md for the one-time Xcode setup).
 */
fun CanvasViewController(): UIViewController = createCanvasHost().viewController

/**
 * Richer host that also exposes the [CanvasController] and the [CanvasShortcuts] table — used by
 * the Swift parent VC to register `UIKeyCommand`s for the Discoverability HUD and dispatch them
 * back into Compose state. The bare [CanvasViewController] entry above keeps backward compatibility
 * for hosts that don't need the keyboard bridge.
 */
class CanvasHost(
    val viewController: UIViewController,
    val controller: CanvasController,
    val shortcuts: List<CanvasShortcut>,
)

fun createCanvasHost(
    theme: @Composable (dark: Boolean, content: @Composable () -> Unit) -> Unit =
        { dark, c -> CanvasTheme(dark, c) },
    content: @Composable () -> Unit = { CanvasContent() },
): CanvasHost {
    // No external scope — `CanvasRoot` calls `controller.attachScope(rememberCoroutineScope())`
    // on first composition, which is the only scope that carries a `MonotonicFrameClock` and so
    // can drive `animate { ... }` on iOS. Swift dispatches that arrive before that scope is
    // attached no-op silently rather than crashing.
    val controller = CanvasController()
    val viewController = ComposeUIViewController { CanvasRoot(controller, theme, content) }
    return CanvasHost(viewController, controller, CanvasShortcuts)
}
