package com.kmpile.canvas.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kmpile.canvas.CanvasApp
import com.kmpile.canvas.CanvasShortcuts

/**
 * Android host for the multiplatform canvas — the single launcher activity of the standalone
 * **Canvas** app (`:androidApp`, a thin wrapper over the `:canvas` module). This is a separate app
 * (not an activity embedded in some host), mirroring iOS (the Swift app) and desktop/web
 * (`:canvas:hotRun` / `:canvas:wasmJsBrowserDevelopmentRun`). Edit the gitignored CanvasContent.kt,
 * rebuild + reinstall (no hot reload on Android), then open the Canvas icon.
 */
class CanvasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CanvasApp() }
    }

    /**
     * Feed Android's system **Keyboard Shortcuts Helper** (Meta+/ on Chromebook / BT-keyboarded
     * phones+tablets) from the shared [CanvasShortcuts] table — purely informational, key handling
     * stays in Compose's onKeyEvent. Entries without a [CanvasShortcut.key] are gesture-only and
     * skipped here (they still appear in the in-app cheat-sheet).
     */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val infos =
            CanvasShortcuts.mapNotNull { shortcut ->
                val key = shortcut.key?.firstOrNull() ?: return@mapNotNull null
                val mods =
                    (if (shortcut.cmd) KeyEvent.META_META_ON else 0) or
                        (if (shortcut.shift) KeyEvent.META_SHIFT_ON else 0) or
                        (if (shortcut.alt) KeyEvent.META_ALT_ON else 0)
                KeyboardShortcutInfo(shortcut.label, key, mods)
            }
        data?.add(KeyboardShortcutGroup("Canvas", infos))
    }
}
