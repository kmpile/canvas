package com.kmpile.canvas

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
// Pointer-event Ctrl accessor, aliased: the KeyEvent version of this same name
// (androidx.compose.ui.input.key.isCtrlPressed) is imported above for onKeyEvent.
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Two-finger scroll / mouse wheel → pan, in px per scroll unit. */
private const val ScrollPanFactor = 40f

/**
 * Ctrl + scroll → zoom (centered). The desktop/web equivalent of the macOS trackpad pinch the host
 * feeds in: Windows/Linux touchpads can't deliver a pinch to Compose/AWT, so the conventional
 * Ctrl-scroll zoom (browsers/Figma) covers them. The scroll delta is normalized to wheel "notches"
 * via [ScrollZoomUnitsPerNotch] (platforms scale wheel deltas differently — desktop ±1, browser
 * ±120), then [ZoomStepPerNotch] is applied multiplicatively per notch so one wheel rotation is a
 * consistent ~Chrome-sized step everywhere. [MaxZoomNotchesPerEvent] caps a single coarse/fast
 * scroll event so it can't overshoot.
 */
private const val ZoomStepPerNotch = 1.2f
private const val MaxZoomNotchesPerEvent = 2f

/**
 * Clamp the pill's drag offset so the whole pill stays on screen. The pill is anchored bottom-end
 * and [offset] slides it from there: negative moves it toward the top-left (into view), positive
 * would push it past the anchored corner. [pill] is the pill's full measured footprint (incl. its
 * padding), so the valid range keeps every edge inside [viewport]. No-op until both are measured.
 */
private fun clampPillOffset(
    offset: Offset,
    viewport: Size,
    pill: IntSize,
): Offset {
    if (viewport == Size.Zero || pill == IntSize.Zero) return offset
    val minX = (pill.width - viewport.width).coerceAtMost(0f)
    val minY = (pill.height - viewport.height).coerceAtMost(0f)
    return Offset(offset.x.coerceIn(minX, 0f), offset.y.coerceIn(minY, 0f))
}

/**
 * First-class keyboard support, collision-safe with arbitrary `CanvasContent`:
 *  - **`Cmd/Ctrl+Shift+<key>`** for every canvas action (mode/rotate/theme/chrome/help). Modifier-
 *    heavy combos never collide with typed text and are essentially never bound by previewed apps.
 *  - **`Shift+0` / `Shift+1`** for zoom presets — Figma convention, web-safe (unlike `Cmd+0` which
 *    browsers eat), and handed off via a *bubbling* onKeyEvent so a focused text field in
 *    `CanvasContent` keeps `Shift+0` for typing `)`.
 *  - **`Space`-hold** = momentary pan (Figma/Photoshop). Same bubbling handoff — a focused
 *    `BasicTextField` consumes the spacebar first.
 *  - **`Esc`** is only intercepted while the cheat-sheet is open, so it doesn't shadow content's
 *    own dismiss.
 */

/**
 * Shared canvas harness — renders the [content] slot full-bleed at natural 1:1 size, wrapped in the
 * [theme] slot, under a floating control pill (zoom %, tap to reset; day/
 * night switch). Navigate with **pinch = zoom** + **drag/two-finger = pan** (Compose transform
 * gestures on touch; mouse-drag + scroll on desktop/web). Desktop/web also zoom via **Ctrl +
 * scroll** (macOS additionally gets trackpad pinch from its host). Each platform wraps this in its
 * own host (desktop window w/ hot reload, web canvas, Android activity, iOS view controller).
 */
@Composable
fun CanvasRoot(
    controller: CanvasController = rememberCanvasController(),
    // Theme slot — wraps the whole canvas (pill included) so a host can preview content in its own
    // app palette. Defaults to the bundled minimal [CanvasTheme]; pass e.g.
    // `{ dark, c -> MyAppTheme(dark, c) }` to render in your real colours.
    theme: @Composable (dark: Boolean, content: @Composable () -> Unit) -> Unit =
        { dark, c -> CanvasTheme(dark, c) },
    // The composable being previewed, full-bleed at natural 1:1 size.
    content: @Composable () -> Unit,
) {
    // Hand the controller a Compose-aware scope — `animate { ... }` inside `animateTo` needs the
    // `MonotonicFrameClock` that `rememberCoroutineScope()` provides; on iOS a bare-
    // `Dispatchers.Main` scope made by the Swift bridge throws `IllegalStateException` on the
    // simulator (and silently no-ops on device).
    val composeScope = rememberCoroutineScope()
    LaunchedEffect(controller, composeScope) { controller.attachScope(composeScope) }
    val systemDark = isSystemInDarkTheme()
    // Follow the system theme: seed at first composition AND re-sync whenever the OS flips (e.g.,
    // macOS auto switches at sunset). The user can still override via the pill / Cmd+Shift+A / HUD
    // — their toggle stands until the next system change, at which point we re-follow.
    LaunchedEffect(systemDark) { controller.dark = systemDark }
    // On platforms where the OS rotates the app viewport (iOS / Android), mirror that into
    // [controller.orientation] so `FramePreview` re-lays out without any user input — pure
    // device-rotation. Desktop / web ignore viewport aspect (window shape isn't user intent).
    if (OrientationFollowsDevice) {
        LaunchedEffect(controller.viewportSize) {
            val size = controller.viewportSize
            if (size != Size.Zero) {
                controller.setOrientation(
                    if (size.width > size.height) {
                        CanvasOrientation.Landscape
                    } else {
                        CanvasOrientation.Portrait
                    },
                )
            }
        }
    }
    // Pill-position offset from its default bottom-right anchor — dragged via the drag-indicator
    // (the zoom-% morphs into one in interact mode, where its tap-to-reset is disabled anyway).
    // Persists across mode flips so the pill stays where you put it. On viewport resize (orientation
    // flip, window resize) we scale the offset proportionally so the pill keeps roughly the same
    // relative place, then [clampPillOffset] re-snaps it inside the new bounds so it can never end
    // up off-screen (e.g. a tall-portrait offset on a short landscape viewport). [pillSize] is the
    // pill's measured footprint, needed by the clamp.
    var pillOffset by remember { mutableStateOf(Offset.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }

    // Easter egg: rapid day/night flips play the "Day 'n' Nite" hook, one note per flip — observed
    // off the controller so it fires regardless of who triggered the flip (pill switch, key, or
    // native HUD).
    val chime = remember { DayNiteChime() }
    LaunchedEffect(controller) {
        var first = true
        snapshotFlow { controller.dark }.collect {
            if (!first) chime.onFlip()
            first = false
        }
    }

    // Keyboard support is active only where there's a physical keyboard (desktop/web always,
    // Android dynamically when a BT/USB keyboard is attached, iOS never).
    val keyboardShortcuts = SupportsKeyboardShortcuts
    val focusRequester = remember { FocusRequester() }
    // Grab focus once, from onGloballyPositioned — i.e. after the focusable node is actually
    // attached. requestFocus() from LaunchedEffect(Unit) runs before attach and silently no-ops on
    // desktop, leaving nothing focused so no key reaches onKeyEvent (all shortcuts dead).
    var keyboardFocusRequested by remember { mutableStateOf(false) }
    // Space-hold = momentary inspect; remember the mode to restore when released.
    var spaceHoldPrev by remember { mutableStateOf<Boolean?>(null) }
    // In-app cheat-sheet (desktop/web only — Android/iOS surface shortcuts via their native HUD).
    // Hover/long-press detection is **scoped to the zoom-%/drag-handle** child inside the pill, not
    // the whole pill body — the rest of the pill has affordances of its own (toggles/switches) and
    // hovering them shouldn't pop the cheat-sheet. The same [pillZoomInteractionSource] is shared
    // with the [ShortcutsCard] below, so moving the cursor between the % and the card counts as
    // "still hovering" and doesn't trigger an Exit-dismiss. Hover-dwell + dismiss-on-exit both
    // route through the controller, so there's a single rendering path (the standalone card above
    // the pill, gated by `controller.showShortcuts`).
    val showInAppShortcuts = keyboardShortcuts && !HasNativeShortcutsHud
    val pillZoomInteractionSource =
        if (showInAppShortcuts) remember { MutableInteractionSource() } else null
    if (pillZoomInteractionSource != null) {
        LaunchedEffect(pillZoomInteractionSource, controller) {
            var showJob: Job? = null
            var hideJob: Job? = null
            // A click (or a pill-drag started from the handle) suppresses the dwell until the cursor
            // genuinely leaves the hit-target. [dragging] tells a real leave apart from the Exit/Enter
            // churn the handle emits as it slides under the cursor while being dragged — that churn
            // must NOT clear the suppression, or the dwell re-arms and pops when the drag stops.
            // Applies in both modes (% in inspect, drag-handle in interact).
            var suppressed = false
            var dragging = false
            pillZoomInteractionSource.interactions.collect { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> {
                        hideJob?.cancel()
                        hideJob = null
                        if (!suppressed && !controller.showShortcuts) {
                            showJob?.cancel()
                            showJob =
                                launch {
                                    delay(ShortcutsRevealDelayMs)
                                    if (!controller.showShortcuts) controller.toggleShortcuts()
                                }
                        }
                    }

                    is HoverInteraction.Exit -> {
                        showJob?.cancel()
                        showJob = null
                        // Only a leave while NOT dragging is a genuine exit: drop the suppression so
                        // the next Enter re-arms, and dismiss-on-leave. The drag's own Exit/Enter
                        // churn (dragging == true) is ignored so the suppression survives the drag.
                        if (!dragging) {
                            suppressed = false
                            hideJob?.cancel()
                            hideJob =
                                launch {
                                    delay(ShortcutsHideGraceMs)
                                    if (controller.showShortcuts) controller.dismissShortcuts()
                                }
                        }
                    }

                    is PressInteraction.Press -> {
                        // Clicked the hit-target — cancel the pending reveal and block re-arming until
                        // a genuine leave + re-enter. Clicks on *other* pill controls (inspect toggle /
                        // switches) don't emit on this source, so they're ignored.
                        suppressed = true
                        showJob?.cancel()
                        showJob = null
                    }

                    is DragInteraction.Start -> {
                        // Dragging the pill by the handle: keep suppressing and mark the drag so the
                        // handle's churn-Exits below don't clear it.
                        dragging = true
                        suppressed = true
                        showJob?.cancel()
                        showJob = null
                    }

                    is DragInteraction.Stop, is DragInteraction.Cancel -> {
                        // Drag finished; cursor is still on the handle, so stay suppressed — a fresh
                        // dwell only re-arms after a real leave (handled in Exit) and re-enter.
                        dragging = false
                    }
                }
            }
        }
    }

    theme(controller.dark) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Bubbling (not preview) handler: a focused control/field in the previewed
                        // content gets keys first; the canvas only acts on what the content ignores.
                        .then(
                            if (!keyboardShortcuts) {
                                Modifier
                            } else {
                                Modifier
                                    .focusRequester(focusRequester)
                                    .focusable()
                                    .onGloballyPositioned {
                                        // Focusable node is attached now — grab focus once so the canvas
                                        // receives shortcuts immediately, without needing a click first.
                                        if (!keyboardFocusRequested) {
                                            keyboardFocusRequested = true
                                            runCatching { focusRequester.requestFocus() }
                                        }
                                    }.onKeyEvent { event ->
                                        val cmd = event.isMetaPressed || event.isCtrlPressed
                                        val shift = event.isShiftPressed
                                        val alt = event.isAltPressed
                                        val down = event.type == KeyEventType.KeyDown
                                        // Modeless actions go through controller.dispatch so the native
                                        // HUD bridges and the keyboard handler route through one place.
                                        // Auto-repeat (held key firing KeyDown repeatedly on JVM/AWT) is
                                        // mostly benign here: the animated actions cancel and re-target an
                                        // in-flight tween rather than stacking, so we skip a downKeys set
                                        // (which proved fragile when KeyUp wasn't reliably delivered with
                                        // modifiers held).
                                        val id: String? =
                                            when {
                                                down && cmd && shift && event.key == Key.H -> CanvasActions.Inspect

                                                down && cmd && shift && event.key == Key.V -> CanvasActions.Interact

                                                down && shift && !cmd && event.key == Key.Zero -> CanvasActions.Zoom100

                                                down && shift && !cmd && event.key == Key.One -> CanvasActions.ZoomFill

                                                // ⌘⌥R, not ⌘⇧R — Chrome reserves ⌘⇧R for hard-reload.
                                                down && cmd && alt && event.key == Key.R -> CanvasActions.Rotate

                                                down && cmd && alt && event.key == Key.A -> CanvasActions.DayNight

                                                down && cmd && shift && event.key == Key.Slash -> CanvasActions.Shortcuts

                                                else -> null
                                            }
                                        when {
                                            id != null -> {
                                                controller.dispatch(id)
                                                true
                                            }

                                            // Space-hold = momentary inspect (Figma/Photoshop). Modal
                                            // down/up; tracked locally because dispatch is one-shot. The
                                            // bubbling handler still hands the key to a focused text field
                                            // in CanvasContent first.
                                            event.key == Key.Spacebar -> {
                                                when (event.type) {
                                                    KeyEventType.KeyDown -> {
                                                        if (spaceHoldPrev == null) {
                                                            spaceHoldPrev = controller.inspect
                                                            controller.inspect = true
                                                        }
                                                        true
                                                    }

                                                    KeyEventType.KeyUp -> {
                                                        spaceHoldPrev?.let { controller.inspect = it }
                                                        spaceHoldPrev = null
                                                        true
                                                    }

                                                    else -> {
                                                        false
                                                    }
                                                }
                                            }

                                            down && event.key == Key.Escape && controller.showShortcuts -> {
                                                controller.dismissShortcuts()
                                                true
                                            }

                                            else -> {
                                                false
                                            }
                                        }
                                    }
                            },
                        ),
            ) {
                // Content layer — full-bleed, transformed by the controller. It attaches no pointer
                // handlers of its own: in interact mode no overlay sits above it, so every event
                // reaches CanvasContent natively.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { newSize ->
                                val size = newSize.toSize()
                                val old = controller.viewportSize
                                // On a real resize (skip the first measurement from Size.Zero), scale
                                // the pill offset proportionally so it keeps roughly the same relative
                                // spot when the screen rotates or the window resizes, then clamp it into
                                // the new bounds so it stays fully visible.
                                if (old != Size.Zero && size != Size.Zero &&
                                    (old.width != size.width || old.height != size.height)
                                ) {
                                    pillOffset =
                                        clampPillOffset(
                                            Offset(
                                                pillOffset.x * (size.width / old.width),
                                                pillOffset.y * (size.height / old.height),
                                            ),
                                            size,
                                            pillSize,
                                        )
                                }
                                controller.viewportSize = size
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    scaleX = controller.scale
                                    scaleY = controller.scale
                                    translationX = controller.offset.x
                                    translationY = controller.offset.y
                                }
                                // Natural (unscaled) content size — graphicsLayer is draw-only, so this
                                // reports the pre-zoom layout size used to compute the fill scale.
                                .onSizeChanged { controller.setContentSize(it.toSize()) },
                    ) {
                        // Expose orientation so rotation-aware content (e.g. FramePreview) can swap
                        // its width/height instead of merely being tilted.
                        CompositionLocalProvider(
                            LocalCanvasOrientation provides controller.orientation,
                        ) {
                            content()
                        }
                    }
                }

                // Gesture-capture layer — composed only in inspect mode. A full-screen sibling
                // drawn above the content that owns pan/zoom and swallows every event, so nothing leaks
                // through to CanvasContent. It lives in untransformed viewport space, matching the
                // coordinates the controller assumes (e.g. toggleZoomAt anchors on the viewport center).
                // The pills are composed after this, so they stay on top and tappable in both modes.
                if (controller.inspect) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                // Catch-all, listed first so it is the OUTERMOST handler and therefore runs
                                // the Main pass LAST — after the detectors below have claimed their gestures.
                                // It consumes whatever they left, so no event reaches the content sibling
                                // behind it (airtight blocking, not reliant on the detectors incidentally
                                // consuming everything).
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent().changes.forEach { it.consume() }
                                        }
                                    }
                                }
                                // Native multitouch pinch + pan on Android/iOS (one-pointer drag = pan on
                                // desktop/web). Desktop trackpad pinch comes from the host via the controller.
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        controller.zoomBy(zoom)
                                        controller.panBy(pan)
                                    }
                                }
                                // Double-tap (touch) / double-click (mouse) → toggle 100% ⇄ fill, anchored
                                // on the tap so that point stays put.
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { controller.toggleZoomAt(it) })
                                }
                                // Scroll wheel / two-finger scroll on desktop/web:
                                //  - plain          → pan the content
                                //  - Ctrl + scroll  → zoom (centered). Windows/Linux touchpads can't
                                //    deliver a pinch to Compose, so this is their zoom gesture; macOS
                                //    keeps its trackpad pinch via the host. Ctrl (not ⌘) because ⌘/Win
                                //    is OS-reserved on Windows and wouldn't reach the app. Touch zoom
                                //    stays on pinch via detectTransformGestures above.
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Scroll) {
                                                val d = event.changes.first().scrollDelta
                                                if (event.keyboardModifiers.isPointerCtrlPressed) {
                                                    // Wheel-up (negative scrollDelta.y) zooms in. Normalize
                                                    // the platform's wheel delta to notches, then one
                                                    // multiplicative step per notch (~Chrome's ladder).
                                                    val notches =
                                                        (-d.y / ScrollZoomUnitsPerNotch)
                                                            .coerceIn(-MaxZoomNotchesPerEvent, MaxZoomNotchesPerEvent)
                                                    controller.zoomBy(ZoomStepPerNotch.pow(notches))
                                                } else {
                                                    controller.panBy(Offset(-d.x, -d.y) * ScrollPanFactor)
                                                }
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                },
                    )
                }

                // Tap-anywhere-to-dismiss layer behind the popup (the pill/popup sit on top of it).
                if (controller.showShortcuts) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { controller.dismissShortcuts() },
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            // User-drag offset (zero by default; updated by ControlPill's percent-drag in
                            // interact mode). Layout positions to bottom-end; offset slides from there.
                            .offset { IntOffset(pillOffset.x.roundToInt(), pillOffset.y.roundToInt()) }
                            // Measure the FULL footprint — including the safe-area insets + spacing added
                            // below — by sitting BEFORE those padding modifiers in the chain. The clamp
                            // then keeps the whole padded box on-screen, so the visible body can't be
                            // dragged under the status bar / off any edge. (After the paddings,
                            // onSizeChanged would report only the inner pill and the clamp would hand back
                            // exactly the inset as extra travel — the bug that let the top slip off.)
                            .onSizeChanged { pillSize = it }
                            // Keep clear of the system bars on every edge — status bar (Android) / notch
                            // + home indicator (iOS) below, status bar above (matters once the pill is
                            // dragged up); no-op on desktop/web.
                            .systemBarsPadding()
                            .padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (controller.showShortcuts) {
                        // Share the pill's hover source so the card counts as "still hovering";
                        // moving the cursor from the % onto the card doesn't trigger the
                        // dismiss-on-exit grace timer.
                        ShortcutsCard(
                            modifier =
                                if (pillZoomInteractionSource != null) {
                                    Modifier.hoverable(pillZoomInteractionSource)
                                } else {
                                    Modifier
                                },
                        )
                    }
                    ControlPill(
                        dark = controller.dark,
                        scale = controller.scale,
                        inspect = controller.inspect,
                        onInspectChange = { controller.inspect = it },
                        onDarkChange = { controller.dark = it },
                        onReset = { controller.reset() },
                        onDragPercent = { delta ->
                            pillOffset = clampPillOffset(pillOffset + delta, controller.viewportSize, pillSize)
                        },
                        // Long-press → cheat-sheet is only useful when there's no native HUD; on
                        // Android/iOS the system surfaces shortcuts already, so the long-press
                        // would duplicate that route.
                        shortcutsEnabled = showInAppShortcuts,
                        onShowShortcuts = { controller.toggleShortcuts() },
                        zoomInteractionSource = pillZoomInteractionSource,
                    )
                }
            }
        }
    }
}

/**
 * Floating canvas control pill: inspect ⇄ interact toggle, zoom % (with drag-to-move in interact
 * mode — see [PillZoomIndicator]), then a sun/moon day-night switch. Each control wraps to its
 * natural width with no extra inter-control spacing — the gap to the % comes from the indicator's
 * own horizontal padding. The indicator's width is pinned to "1000%" so the pill doesn't reflow as
 * the zoom % changes.
 *
 * The pill renders as a plain Material tonal `Surface` — it sits on top of arbitrary previewed
 * content, and an opaque tonal surface keeps the controls legible in every previewed state.
 */
@Composable
private fun ControlPill(
    dark: Boolean,
    scale: Float,
    inspect: Boolean,
    onInspectChange: (Boolean) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    shortcutsEnabled: Boolean,
    onShowShortcuts: () -> Unit,
    onDragPercent: (Offset) -> Unit = {},
    zoomInteractionSource: MutableInteractionSource? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Checked = inspect (canvas owns pan/zoom — the default; primary fill flags "active");
            // unchecked = interact (events reach the content).
            IconToggleButton(
                checked = inspect,
                onCheckedChange = onInspectChange,
                colors =
                    IconButtonDefaults.iconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    imageVector = if (inspect) Icons.Outlined.PanTool else Icons.Outlined.TouchApp,
                    contentDescription = if (inspect) "Inspect (pan/zoom canvas)" else "Interact (events go to content)",
                )
            }
            PillZoomIndicator(
                scale = scale,
                inspect = inspect,
                onReset = onReset,
                onDrag = onDragPercent,
                onShowShortcuts = onShowShortcuts,
                shortcutsEnabled = shortcutsEnabled,
                interactionSource = zoomInteractionSource,
            )
            PillModeSwitch(checked = dark, onCheckedChange = onDarkChange) {
                Text(text = if (dark) "☾" else "☀", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private const val ShortcutsRevealDelayMs = 5000L

/** Grace period after the cursor leaves the % / card before the cheat-sheet auto-dismisses,
 *  giving the cursor time to cross the gap between the two without flicker. */
private const val ShortcutsHideGraceMs = 200L

/**
 * Zoom % ⇄ drag-handle hit-target. Inspect mode: shows the %, tap resets, long-press opens the
 * shortcuts cheat-sheet on keyboard platforms. Interact mode: the % crossfades to a drag-indicator
 * grip and the gesture is reclaimed for drag-to-move the whole pill out of the way of live content
 * (the tap-to-reset is moot in this mode anyway, so the gesture is free).
 *
 * When [interactionSource] is non-null (desktop/web, no native HUD), `.hoverable` is attached so a
 * hover-dwell ([ShortcutsRevealDelayMs]) over this hit-target — and *only* this hit-target, not the
 * entire pill — opens the cheat-sheet (collected in [CanvasRoot]). A click cancels that dwell and
 * blocks re-arming it until the cursor leaves and returns (see the interaction collector in
 * [CanvasRoot]).
 */
@Composable
private fun PillZoomIndicator(
    scale: Float,
    inspect: Boolean,
    onReset: () -> Unit,
    onDrag: (Offset) -> Unit,
    onShowShortcuts: () -> Unit,
    shortcutsEnabled: Boolean,
    interactionSource: MutableInteractionSource? = null,
) {
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .then(if (interactionSource != null) Modifier.hoverable(interactionSource) else Modifier)
                .pointerInput(inspect, interactionSource) {
                    detectTapGestures(
                        // onPress fires on the initial down. Emitting `PressInteraction.Press` onto
                        // the shared source lets [CanvasRoot]'s hover collector cancel any pending
                        // dwell — a tap on this exact hit-target shouldn't be followed by the
                        // cheat-sheet popping up after the cursor drifts off.
                        onPress = { offset ->
                            // Balance the Press with a Release/Cancel so the shared source doesn't
                            // accumulate unreleased presses. The collector only acts on Press; emitting
                            // the close keeps the interaction stream well-formed for any other consumer.
                            val press = PressInteraction.Press(offset)
                            interactionSource?.tryEmit(press)
                            val released = tryAwaitRelease()
                            interactionSource?.tryEmit(
                                if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                            )
                        },
                        onLongPress = if (shortcutsEnabled) ({ onShowShortcuts() }) else null,
                        onTap = { if (inspect) onReset() },
                    )
                }.pointerInput(inspect, interactionSource) {
                    if (!inspect) {
                        // Emit Drag Start/Stop onto the shared source so [CanvasRoot]'s collector can keep
                        // the cheat-sheet suppressed across the whole drag — and ignore the Exit/Enter
                        // churn the moving handle produces — instead of re-arming the dwell mid-drag.
                        var dragStart: DragInteraction.Start? = null
                        detectDragGestures(
                            onDragStart = {
                                val start = DragInteraction.Start()
                                dragStart = start
                                interactionSource?.tryEmit(start)
                            },
                            onDragEnd = {
                                dragStart?.let { interactionSource?.tryEmit(DragInteraction.Stop(it)) }
                                dragStart = null
                            },
                            onDragCancel = {
                                dragStart?.let { interactionSource?.tryEmit(DragInteraction.Cancel(it)) }
                                dragStart = null
                            },
                        ) { _, dragAmount -> onDrag(dragAmount) }
                    }
                }
                // The only gap between the %/handle and the flanking controls (the Row adds no spacing).
                // Asymmetric on purpose: the left IconToggleButton already supplies its own visual gap via
                // the 48.dp minimumInteractiveComponentSize touch inset, while the right Switch's track
                // fills its box flush — so no start padding, and a larger end padding to balance the two
                // so the % reads centered (otherwise it sits shifted toward the switch).
                .padding(start = 0.dp, end = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Invisible widest-value placeholder pins the width so the pill never reflows (20%…1000%).
        Text(text = "1000%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.alpha(0f))
        // matchParentSize + inner centering keeps both contents anchored to the box centre — without
        // it Crossfade lays content at top-start, so the smaller icon drifts in from top-left.
        Crossfade(
            targetState = inspect,
            modifier = Modifier.matchParentSize(),
            label = "canvasPillModeIndicator",
        ) { showPercent ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (showPercent) {
                    Text(
                        text = "${(scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.DragIndicator,
                        contentDescription = "Drag to move pill",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Switch styled as a *mode* toggle (not on/off): track + border stay constant across states so
 * neither position reads as "active" — only the thumb glyph signals the mode. Thumb uses the
 * primaryContainer/onPrimaryContainer pair in every state for legibility in all themes.
 */
@Composable
private fun PillModeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    thumbContent: @Composable () -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                checkedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                uncheckedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                checkedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                checkedBorderColor = MaterialTheme.colorScheme.outline,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        thumbContent = thumbContent,
    )
}

/** Cheat-sheet popup listing the keyboard shortcuts, styled to match the (always-Material) pill. */
@Composable
private fun ShortcutsCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Shortcuts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CanvasShortcuts.forEach { shortcut ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeyCap(shortcut.display)
                    Text(
                        text = shortcut.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Width that keeps the key column aligned across rows. */
private val KeyCapMinWidth = 48.dp

@Composable
private fun KeyCap(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .widthIn(min = KeyCapMinWidth)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Shared canvas state. Cross-platform gestures (touch pinch/pan, scroll) drive it directly; a
 * platform host can also drive it — e.g. the desktop trackpad-magnify gesture (which Compose can't
 * deliver) feeds [zoomBy] from the host's AWT listener, and iOS's `UIKeyCommand` handlers route
 * through [dispatch] to flip mode/theme/chrome from the system Discoverability HUD.
 */
class CanvasController(
    initialScope: CoroutineScope? = null,
) {
    /**
     * Compose-aware scope used for [animateTo]'s `animate { ... }` calls. Must include a
     * `MonotonicFrameClock` — a bare `Dispatchers.Main` scope throws `IllegalStateException: A
     * MonotonicFrameClock is not available in this CoroutineContext` on iOS. [CanvasRoot] attaches
     * `rememberCoroutineScope()` (which provides one via the Recomposer's context) on first
     * composition; until then [animateTo] is a no-op rather than crashing.
     */
    private var scope: CoroutineScope? = initialScope

    internal fun attachScope(scope: CoroutineScope) {
        this.scope = scope
    }

    var scale by mutableStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Viewport size in pixels — fed to [fillScale]; on mobile [OrientationFollowsDevice]=true
     *  platforms, a LaunchedEffect in [CanvasRoot] also syncs [orientation] to its aspect.
     *  Public set because the layout's `onSizeChanged` is the source. */
    var viewportSize by mutableStateOf(Size.Zero)

    /**
     * Layout orientation passed to [LocalCanvasOrientation]. Driven by:
     *  - [toggleOrientation] — hotkey/pill rotation toggles it (any platform).
     *  - The viewport on mobile platforms ([OrientationFollowsDevice]=true) — physical device
     *    rotation is mirrored here so a `FramePreview` reflows automatically, no keyboard needed.
     *
     * Defaults to portrait so non-mobile callers start with a sensible phone-shaped preview even
     * when the host window happens to be landscape-shaped.
     */
    var orientation by mutableStateOf(CanvasOrientation.Portrait)
        private set

    internal fun setOrientation(value: CanvasOrientation) {
        orientation = value
    }

    private var animation: Job? = null

    /**
     * Inspect mode (pill toggle **pressed**): the canvas owns pan/zoom and a full-screen overlay
     * swallows events. Interact mode — the **default** (toggle unpressed) — attaches no pointer
     * handlers, so every event reaches [CanvasContent] natively (needed to poke a previewed component
     * that has its own scroll/drag/zoom gestures). The pill toggle binds to this.
     */
    var inspect by mutableStateOf(false)

    /** Day/night appearance. Initial value seeded from `isSystemInDarkTheme()` by CanvasRoot. */
    var dark by mutableStateOf(false)

    /** Whether the in-app shortcuts cheat-sheet (desktop/web fallback) is open. */
    var showShortcuts by mutableStateOf(false)

    fun toggleDark() {
        dark = !dark
    }

    fun toggleShortcuts() {
        showShortcuts = !showShortcuts
    }

    fun dismissShortcuts() {
        showShortcuts = false
    }

    /**
     * Dispatch a shortcut by its [CanvasShortcut.id] — the single entry point the native HUD bridges
     * (Android `onProvideKeyboardShortcuts`, iOS `UIKeyCommand`) call into. Modeless actions only —
     * the `Space`-hold "pan" shortcut has its own down/up tracking and isn't routed here.
     */
    fun dispatch(id: String) {
        when (id) {
            CanvasActions.Inspect -> inspect = true
            CanvasActions.Interact -> inspect = false
            CanvasActions.Zoom100 -> reset()
            CanvasActions.ZoomFill -> fill()
            CanvasActions.Rotate -> toggleOrientation()
            CanvasActions.DayNight -> toggleDark()
            CanvasActions.Shortcuts -> toggleShortcuts()
        }
    }

    // `viewportSize` is the state-backed declaration near the top; only `contentSize` lives here.
    private var contentSize = Size.Zero

    internal fun setContentSize(size: Size) {
        contentSize = size
    }

    // Continuous gestures are instant (and cancel any running zoom animation).
    fun zoomBy(factor: Float) {
        if (!inspect) return
        animation?.cancel()
        scale = (scale * factor).coerceIn(MinScale, MaxScale)
    }

    fun panBy(delta: Offset) {
        animation?.cancel()
        offset += delta
    }

    /** Animate to 100% (1:1), centered. Also resets orientation to portrait on desktop/web; on
     *  device-follows platforms (iOS/Android) orientation stays bound to the physical device, so we
     *  leave it alone rather than fight the device-rotation sync. */
    fun reset() {
        if (!OrientationFollowsDevice) orientation = CanvasOrientation.Portrait
        animateTo(1f, Offset.Zero)
    }

    /** Animate to fill (content's shortest side covers the viewport), centered. No-op until sized. */
    fun fill() {
        animateTo(fillScale() ?: return, Offset.Zero)
    }

    /** Toggle [orientation] between Portrait and Landscape. Instant — no visual rotation animation
     *  (which is what introduced the iOS continuation issue earlier). The previewed content
     *  reflows to its new dimensions; `animateContentSize` on `FramePreview` could smooth the
     *  transition if needed. */
    fun toggleOrientation() {
        orientation =
            if (orientation == CanvasOrientation.Portrait) {
                CanvasOrientation.Landscape
            } else {
                CanvasOrientation.Portrait
            }
    }

    /**
     * Double-tap / double-click toggle: at natural size, animate to **fill** (content's shortest side
     * covers the viewport) anchored on [tap] so that point stays under the cursor; at any other zoom,
     * animate back to 100% (centered). No-op in interact mode or before sizes are known.
     */
    fun toggleZoomAt(tap: Offset) {
        if (!inspect) return
        if (abs(scale - 1f) > ZoomEpsilon) {
            reset()
            return
        }
        val target = fillScale() ?: return
        // Keep the tapped point fixed: t' = d - (target/scale) * (d - t), d = tap relative to center
        // (the graphicsLayer scales about the centered box, so center is the transform origin).
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val d = tap - center
        animateTo(target, d - (d - offset) * (target / scale))
    }

    /**
     * Fill scale = content's shortest side covering the viewport; null until both are measured.
     * Uses the measured content size as-is — rotation reflows the previewed content (its measured
     * box already swaps axes), so adjusting for orientation here would double-count and over-zoom.
     */
    private fun fillScale(): Float? {
        val cw = contentSize.width
        val ch = contentSize.height
        if (cw <= 0f || ch <= 0f || viewportSize == Size.Zero) return null
        return max(viewportSize.width / cw, viewportSize.height / ch).coerceIn(MinScale, MaxScale)
    }

    /** Tween scale + offset together to the target; cancels any in-flight animation. */
    private fun animateTo(
        targetScale: Float,
        targetOffset: Offset,
    ) {
        animation?.cancel()
        val scope = scope ?: return
        val fromScale = scale
        val fromOffset = offset
        animation =
            scope.launch {
                animate(0f, 1f, animationSpec = tween(CanvasZoomDurationMs)) { f, _ ->
                    scale = fromScale + (targetScale - fromScale) * f
                    offset = fromOffset + (targetOffset - fromOffset) * f
                }
            }
    }

    private companion object {
        const val ZoomEpsilon = 0.01f
        const val MinScale = 0.2f
        const val MaxScale = 10f
    }
}

@Composable
fun rememberCanvasController(): CanvasController = remember { CanvasController() }
