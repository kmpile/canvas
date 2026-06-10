package com.kmpile.canvas

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * Desktop host for the multiplatform canvas (the `:canvas` module). Hot-reloadable:
 *
 *     ./gradlew :canvas:hotRun --autoReload
 *
 * Cross-platform gestures live in `CanvasRoot`; desktop adds trackpad **pinch** here via the JBR-only
 * Apple gesture API (Compose can't deliver it), feeding the shared `CanvasController`.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Canvas") {
        val controller = rememberCanvasController()
        LaunchedEffect(window) {
            attachPinchZoom(window) { delta -> controller.zoomBy(1f + delta.toFloat()) }
        }
        CanvasRoot(controller) { CanvasContent() }
    }
}

/**
 * Attaches a macOS trackpad pinch (magnify) listener to the window via the JBR-only Apple gesture
 * API, reflectively (no compile-time dep on `com.apple.eawt.event`; needs
 * `--add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED` on the run task). No-op elsewhere.
 */
private fun attachPinchZoom(window: ComposeWindow, onMagnify: (Double) -> Unit) {
    try {
        val gestureListener = Class.forName("com.apple.eawt.event.GestureListener")
        val magnificationListener = Class.forName("com.apple.eawt.event.MagnificationListener")
        val getMagnification = Class.forName("com.apple.eawt.event.MagnificationEvent")
            .getMethod("getMagnification")
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            magnificationListener.classLoader,
            arrayOf(magnificationListener),
        ) { self, method, args ->
            when (method.name) {
                "magnify" -> { onMagnify(getMagnification.invoke(args!![0]) as Double); null }
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.getOrNull(0)
                "toString" -> "PinchZoomListener"
                else -> null
            }
        }
        Class.forName("com.apple.eawt.event.GestureUtilities")
            .getMethod("addGestureListenerTo", javax.swing.JComponent::class.java, gestureListener)
            .invoke(null, window.rootPane, proxy)
    } catch (_: Throwable) {
        // Not a JBR / not macOS — scroll-wheel and Ctrl+scroll zoom still work.
    }
}
