@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.kmpile.canvas

import kotlin.math.pow

/**
 * Web Audio implementation of the day/night chime: one square-wave oscillator per note onset
 * (matching the desktop "Square Lead" voice), scheduled on the [AudioContext] timeline. Triggered
 * from the day/night flip, which only ever follows a user gesture (switch tap / ⌘⌥A), so it lands
 * inside the browser's transient user-activation window and the context is allowed to start.
 */
internal actual fun playChimeNotes(notes: List<ChimeNote>) {
    val spec =
        notes.joinToString(";") { note ->
            val freq = 440.0 * 2.0.pow((note.midi - 69) / 12.0)
            "$freq,${note.startMillis},${note.durationMillis}"
        }
    if (spec.isNotEmpty()) playRiffWebAudio(spec)
}

/** spec = "freqHz,startMs,durMs;…" — schedules each as a square chime on a fresh AudioContext. */
private fun playRiffWebAudio(spec: String): Unit =
    js(
        """{
        var ctx = new (window.AudioContext || window.webkitAudioContext)();
        var now = ctx.currentTime;
        spec.split(';').forEach(function (part) {
            var a = part.split(',');
            var freq = parseFloat(a[0]);
            var t0 = now + parseFloat(a[1]) / 1000;
            var t1 = t0 + parseFloat(a[2]) / 1000;
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.type = 'square';
            osc.frequency.value = freq;
            gain.gain.setValueAtTime(0.0001, t0);
            gain.gain.exponentialRampToValueAtTime(0.2, t0 + 0.01);
            gain.gain.exponentialRampToValueAtTime(0.0001, t1);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start(t0);
            osc.stop(t1 + 0.02);
        });
    }""",
    )
