@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.kmpile.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * Web host for the multiplatform canvas (see the `:canvas` module). Renders [CanvasRoot] full page.
 * Run with:
 *
 *     ./gradlew :canvas:wasmJsBrowserDevelopmentRun
 *
 * then open the served page. Live-reload only (no stateful hot reload on web).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val root = checkNotNull(document.body) { "Cannot start Canvas: document.body is missing" }
    ComposeViewport(root) { CanvasRoot { CanvasContent() } }
}
