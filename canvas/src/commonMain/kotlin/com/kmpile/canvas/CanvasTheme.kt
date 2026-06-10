package com.kmpile.canvas

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Duration (ms) of the canvas zoom/pan tween and the day↔night colour crossfade. */
internal const val CanvasZoomDurationMs = 280

private val LightScheme = lightColorScheme()
private val DarkScheme = darkColorScheme()

/**
 * The canvas's own minimal Material 3 theme. It wraps the previewed content (and the floating control
 * pill) so `MaterialTheme.colorScheme.*` resolves on every platform with no app-specific palette.
 * Flipping [dark] (pill switch / `⌘⌥A` / system change) crossfades **every** Material colour slot over
 * [CanvasZoomDurationMs] rather than hard-cutting, so nothing snaps regardless of which slots the
 * previewed content reads.
 *
 * Swap [LightScheme] / [DarkScheme] for your own `lightColorScheme(...)` / `darkColorScheme(...)` (or a
 * generated brand scheme) if you want the canvas to preview components in your app's real palette.
 */
@Composable
fun CanvasTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val target = if (dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = target.animated(tween(CanvasZoomDurationMs)), content = content)
}

/** Crossfade each colour slot of [this] over [spec] — one animation per slot, fixed call-site count. */
@Composable
private fun ColorScheme.animated(spec: AnimationSpec<Color>): ColorScheme =
    copy(
        primary = primary.crossfade(spec),
        onPrimary = onPrimary.crossfade(spec),
        primaryContainer = primaryContainer.crossfade(spec),
        onPrimaryContainer = onPrimaryContainer.crossfade(spec),
        inversePrimary = inversePrimary.crossfade(spec),
        secondary = secondary.crossfade(spec),
        onSecondary = onSecondary.crossfade(spec),
        secondaryContainer = secondaryContainer.crossfade(spec),
        onSecondaryContainer = onSecondaryContainer.crossfade(spec),
        tertiary = tertiary.crossfade(spec),
        onTertiary = onTertiary.crossfade(spec),
        tertiaryContainer = tertiaryContainer.crossfade(spec),
        onTertiaryContainer = onTertiaryContainer.crossfade(spec),
        background = background.crossfade(spec),
        onBackground = onBackground.crossfade(spec),
        surface = surface.crossfade(spec),
        onSurface = onSurface.crossfade(spec),
        surfaceVariant = surfaceVariant.crossfade(spec),
        onSurfaceVariant = onSurfaceVariant.crossfade(spec),
        surfaceTint = surfaceTint.crossfade(spec),
        inverseSurface = inverseSurface.crossfade(spec),
        inverseOnSurface = inverseOnSurface.crossfade(spec),
        error = error.crossfade(spec),
        onError = onError.crossfade(spec),
        errorContainer = errorContainer.crossfade(spec),
        onErrorContainer = onErrorContainer.crossfade(spec),
        outline = outline.crossfade(spec),
        outlineVariant = outlineVariant.crossfade(spec),
        scrim = scrim.crossfade(spec),
        surfaceBright = surfaceBright.crossfade(spec),
        surfaceDim = surfaceDim.crossfade(spec),
        surfaceContainer = surfaceContainer.crossfade(spec),
        surfaceContainerHigh = surfaceContainerHigh.crossfade(spec),
        surfaceContainerHighest = surfaceContainerHighest.crossfade(spec),
        surfaceContainerLow = surfaceContainerLow.crossfade(spec),
        surfaceContainerLowest = surfaceContainerLowest.crossfade(spec),
    )

@Composable
private fun Color.crossfade(spec: AnimationSpec<Color>): Color = animateColorAsState(this, spec).value
