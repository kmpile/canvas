package com.kmpile.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Render a **sketched** (mock, not-the-real-app) screen full-bleed but confined to the safe zone —
 * the same `systemBarsPadding()` the floating control pill uses (see `CanvasRoot`), so the sketch
 * never runs under the status bar / bottom nav. Use this for rough screen mockups authored directly
 * in [CanvasContent]; use [FramePreview] instead when previewing a real screen's stateless `*Content`
 * at fixed phone size.
 *
 * Insets are real on Android/iOS and empty on desktop (the window has no system bars to avoid), so a
 * sketch sits flush to the desktop window edge — which is correct there. Swap `systemBarsPadding()`
 * for `safeDrawingPadding()` below if a sketch also needs display-cutout / IME clearance.
 */
@Composable
fun SketchPreview(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
    ) {
        content()
    }
}
