# Canvas

A tiny **Compose Multiplatform** harness for previewing a single composable in isolation —
full-bleed, zoomable, with day/night and inspect⇄interact toggles. Runs as its own app on
**desktop** (hot reload), **web** (wasm), **Android**, and **iOS**.

```
┌─────────────────────────────────┐
│        your composable          │
│         (1:1, zoomable)         │
│                       ╭───────╮ │
│                       │👆 100% ☀│ │  ← floating control pill
│                       ╰───────╯ │
└─────────────────────────────────┘
```

## What you edit

`canvas/src/commonMain/kotlin/com/kmpile/canvas/CanvasContent.kt` — the body of
`CanvasContent()`. It's **gitignored** (seeded from a template), so scratch edits never get
committed. Reset to default by deleting the file + rebuilding. Two wrappers for screen-sized content:

- **`FramePreview { … }`** — renders at a fixed screen size with a matching `LocalWindowInfo`
  (so size-class layout resolves for that frame). Defaults to `AppleDevice.IPhone16`; pass any
  `AppleDevice` / `PixelDevice` preset or a custom `Frame` — `FramePreview(PixelDevice.PixelProXL) { … }`.
- **`SketchPreview { … }`** — full-bleed within the safe zone, for rough mock screens.

## Run

| Target | Command | Notes |
|--------|---------|-------|
| Desktop | `./gradlew :canvas:hotRun --autoReload` | Stateful hot reload; scroll/Ctrl+scroll to zoom, drag to pan |
| Web | `./gradlew :canvas:wasmJsBrowserDevelopmentRun` | Live reload |
| Android | `./gradlew :androidApp:installDebug` | Then open the **Canvas** icon; rebuild per change |
| iOS | see [`iosApp/README.md`](iosApp/README.md) | One-time Xcode setup |

Without the Apple toolchain (e.g. Windows/Linux), set `CANVAS_SKIP_IOS=1` to drop the iOS targets.

## Keyboard shortcuts (desktop / web)

| Keys | Action |
|------|--------|
| `⌘⇧H` / `⌘⇧V` | Inspect (pan/zoom) ⇄ Interact (events reach content) |
| `Space` (hold) | Momentary pan |
| `⇧0` / `⇧1` | Zoom 100% / fill · `2×click` toggles |
| `⌘⌥R` | Rotate 90° (portrait ⇄ landscape) |
| `⌘⌥A` | Day / night |
| `⌘⇧?` | Cheat-sheet |

Android surfaces these in the system Keyboard Shortcuts Helper (Meta+/); iOS in the
Discoverability HUD (hold ⌘).

## Structure

```
canvas/      the multiplatform harness (library) + desktop & web hosts
androidApp/  thin Android launcher (AGP 9 can't make a KMP module an application)
iosApp/      SwiftUI host + Xcode setup notes
```

Theme by swapping `CanvasTheme`'s color schemes for your app palette.

## License

MIT — see [LICENSE.md](LICENSE.md).
