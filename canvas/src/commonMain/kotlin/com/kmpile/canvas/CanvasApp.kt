package com.kmpile.canvas

import androidx.compose.runtime.Composable

/**
 * [CanvasRoot] previewing the bundled scratch [CanvasContent] — the entry point for standalone
 * hosts outside this module (the Android launcher activity; iOS gets the same default through
 * `createCanvasHost`). [CanvasContent] is internal so external hosts can't name it themselves.
 */
@Composable
fun CanvasApp(
    controller: CanvasController = rememberCanvasController(),
    theme: @Composable (dark: Boolean, content: @Composable () -> Unit) -> Unit =
        { dark, c -> CanvasTheme(dark, c) },
) = CanvasRoot(controller, theme) { CanvasContent() }
