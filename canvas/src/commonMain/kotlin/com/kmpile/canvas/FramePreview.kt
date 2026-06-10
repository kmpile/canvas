package com.kmpile.canvas

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A fixed logical screen size for [FramePreview] — portrait short/long sides in dp, swapped by
 * `⌘⌥R`. Use an [AppleDevice] / [PixelDevice] preset, or build one directly for a custom size.
 */
class Frame(
    val shortSide: Dp,
    val longSide: Dp,
    val cornerRadius: Dp = 24.dp,
)

/**
 * Logical sizes (points == dp) of current Apple devices, grouped by distinct size — each comment
 * lists the models sharing it; 3rd arg ≈ display corner radius. Source: iosref.com/res.
 */
enum class AppleDevice(val frame: Frame) {
    // iPhone — portrait points
    IPhoneSE(Frame(375.dp, 667.dp, 0.dp)),          // SE (2nd / 3rd gen)
    IPhoneMini(Frame(375.dp, 812.dp, 44.dp)),       // 12 / 13 mini
    IPhone(Frame(390.dp, 844.dp, 47.dp)),           // 12, 13, 14, 16e, 17e
    IPhone16(Frame(393.dp, 852.dp, 55.dp)),         // 14 Pro, 15, 15 Pro, 16
    IPhone16Pro(Frame(402.dp, 874.dp, 55.dp)),      // 16 Pro, 17, 17 Pro
    IPhone14ProMax(Frame(428.dp, 926.dp, 53.dp)),   // 12 / 13 Pro Max, 14 Plus
    IPhone16Plus(Frame(430.dp, 932.dp, 55.dp)),     // 14 Pro Max, 15 Plus, 15 Pro Max, 16 Plus
    IPhone16ProMax(Frame(440.dp, 956.dp, 55.dp)),   // 16 Pro Max, 17 Pro Max

    // iPad — portrait points
    IPadMini(Frame(744.dp, 1133.dp, 21.dp)),        // mini (6th / 7th gen)
    IPad(Frame(810.dp, 1080.dp, 0.dp)),             // iPad 7–9 (10.2", home button)
    IPadAir11(Frame(820.dp, 1180.dp, 18.dp)),       // iPad 10 / 11, Air 11" (M2/M3), Air 4 / 5
    IPadPro11(Frame(834.dp, 1210.dp, 18.dp)),       // Pro 11" (M4/M5)
    IPadAir13(Frame(1024.dp, 1366.dp, 18.dp)),      // Air 13" (M2/M3), Pro 12.9" (gen 1–6)
    IPadPro13(Frame(1032.dp, 1376.dp, 18.dp)),      // Pro 13" (M4/M5)
}

/**
 * Logical sizes (dp) of current Google Pixel devices; 3rd arg ≈ display corner radius. 412 dp is
 * the long-standing base width; Pro / Pro XL widened to 427 / 448 with the Pixel 9 generation.
 */
enum class PixelDevice(val frame: Frame) {
    Pixel(Frame(412.dp, 915.dp, 36.dp)),            // Pixel base + a-series
    PixelPro(Frame(427.dp, 952.dp, 40.dp)),         // Pixel 9 Pro
    PixelProXL(Frame(448.dp, 997.dp, 40.dp)),       // Pixel 9 Pro XL
    PixelTablet(Frame(800.dp, 1280.dp, 24.dp)),     // Pixel Tablet (landscape-native)
}

/** [FramePreview] at the given Apple [device]'s screen size. */
@Composable
fun FramePreview(device: AppleDevice, content: @Composable () -> Unit) =
    FramePreview(device.frame, content)

/** [FramePreview] at the given Pixel [device]'s screen size. */
@Composable
fun FramePreview(device: PixelDevice, content: @Composable () -> Unit) =
    FramePreview(device.frame, content)

/**
 * Render [content] at a fixed screen size ([AppleDevice.IPhone16] default) inside a matching
 * `LocalWindowInfo`, so size-class layout (`isWindowTablet()`, `currentWindowAdaptiveInfo()`)
 * resolves for that frame — the standard wrapper for a screen's stateless `*Content`. Honors
 * [LocalCanvasOrientation]: `⌘⌥R` swaps the sides so the screen reflows as landscape, not tilted.
 */
@Composable
fun FramePreview(frame: Frame = AppleDevice.IPhone16.frame, content: @Composable () -> Unit) {
    val landscape = LocalCanvasOrientation.current == CanvasOrientation.Landscape
    val width = if (landscape) frame.longSide else frame.shortSide
    val height = if (landscape) frame.shortSide else frame.longSide
    val density = LocalDensity.current
    val sizePx = with(density) { IntSize(width.roundToPx(), height.roundToPx()) }
    val windowInfo = remember(sizePx) {
        object : WindowInfo {
            override val isWindowFocused: Boolean = true
            override val containerSize: IntSize = sizePx
        }
    }
    CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
        Box(
            modifier = Modifier
                .size(width, height)
                .clip(RoundedCornerShape(frame.cornerRadius))
                .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(frame.cornerRadius)),
        ) {
            content()
        }
    }
}
