package com.kmpile.canvas

import androidx.compose.runtime.compositionLocalOf

/**
 * Logical orientation the canvas is "in" — toggled by [CanvasController.toggleOrientation] (and, on
 * mobile, synced to the device viewport aspect). Content that needs to *re-layout* on rotation reads
 * this and swaps its dimensions/size-classes; the canvas reflows the content rather than visually
 * tilting it. See [FramePreview] for the canonical consumer.
 */
enum class CanvasOrientation { Portrait, Landscape }

/**
 * Current canvas orientation, fed by [CanvasRoot] from [CanvasController.orientation]. Defaults to
 * portrait outside the canvas harness so a `FramePreview` rendered elsewhere behaves as before.
 */
val LocalCanvasOrientation = compositionLocalOf { CanvasOrientation.Portrait }
