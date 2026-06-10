package com.kmpile.canvas

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Easter egg for the day/night switch in [CanvasRoot]. Flip it back and forth quickly and, once a
 * streak builds up, the "Day 'n' Nite" hook plays through in full.
 *
 * Transcribed note-for-note from the song's MIDI synth line (the "day and night, the lonely
 * stoner…" motif): F# E D B descending, then G held three beats, then a G-G-B-B sixteenth pickup
 * back to the top. Laid out on a sixteenth-note grid — each slot is a sixteenth at [stepMillis];
 * `0` means "no onset, let the previous note ring". Two loops of the two-bar phrase, expanded once
 * into [DayNiteRiff] so platform code just sounds the notes. Only the JVM/desktop and web hosts make
 * sound (see `Chime.jvm.kt` / `Chime.wasmJs.kt`); other targets are no-ops.
 */
private val hookBar = intArrayOf(
    66, 0, 0, 0, //  F# (beat 1)
    64, 0, 0, 0, //  E  (beat 2)
    62, 0, 0, 0, //  D  (beat 3)
    59, 0, 0, 0, //  B  (beat 4)
    55, 0, 0, 0, //  G  (beat 5)
    55, 0, 0, 0, //  G  (beat 6)
    55, 0, 0, 0, //  G  (beat 7)
    55, 55, 59, 59, // G G B B pickup (beat 8)
)

/** Milliseconds per riff slot — a 128 BPM sixteenth note, true to the original MIDI. */
private const val stepMillis = 117L

/** Consecutive rapid flips before the hook fires — keeps casual toggling silent. */
private const val triggerStreak = 5

/** A flip more than this long after the previous one starts the streak over. */
private val streakWindow = 1_500.milliseconds

/** A scheduled note: MIDI pitch [midi], sounded [startMillis] into the riff for [durationMillis]. */
internal data class ChimeNote(val midi: Int, val startMillis: Long, val durationMillis: Long)

/**
 * The day/night riff expanded onto its timeline — the music lives here once; platform code just
 * sounds each note. Each onset rings across its trailing rest slots and is released at 9/10 of that
 * span so repeated pitches re-articulate cleanly.
 */
internal val DayNiteRiff: List<ChimeNote> = (hookBar + hookBar).let { grid ->
    buildList {
        grid.forEachIndexed { i, note ->
            if (note <= 0) return@forEachIndexed // 0 = no onset; the previous note keeps ringing.
            var span = stepMillis
            var j = i + 1
            while (j < grid.size && grid[j] <= 0) { span += stepMillis; j++ }
            add(ChimeNote(midi = note, startMillis = i * stepMillis, durationMillis = span * 9 / 10))
        }
    }
}

/** Total wall-clock span of the riff — used to swallow re-triggers while one is still playing. */
private val riffDuration = (hookBar.size * 2 * stepMillis).milliseconds

/**
 * Sounds [notes] on a square-lead voice (matching the original track's synth hook), each at its
 * own offset for its own duration. No-op on platforms without a software synth.
 */
internal expect fun playChimeNotes(notes: List<ChimeNote>)

/**
 * Tracks the rapid-flip streak behind the day/night easter egg. Call [onFlip] from the switch's
 * change handler; the moment [triggerStreak] flips land within [streakWindow] of each other the
 * whole [DayNiteRiff] plays once. A slower flip starts the streak over.
 */
internal class DayNiteChime {
    private var streak = 0
    private var lastFlip: TimeSource.Monotonic.ValueTimeMark? = null
    // When the riff currently playing started, if any. Playback is fire-and-forget (no completion
    // callback), so we suppress re-triggers by its known [riffDuration] rather than probing it.
    private var riffStart: TimeSource.Monotonic.ValueTimeMark? = null

    fun onFlip() {
        // Best-effort easter egg: never let a synth/audio failure — or a hot-reload class-loading
        // hiccup on the lazily-touched file facade (NoClassDefFoundError) — crash the host. Swallow
        // everything (runCatching catches Throwable, Errors included).
        runCatching {
            val prev = lastFlip
            streak = if (prev != null && prev.elapsedNow() <= streakWindow) streak + 1 else 1
            lastFlip = TimeSource.Monotonic.markNow()
            if (streak != triggerStreak) return@runCatching
            // Already mid-riff → don't stack a second one on top of it.
            if (riffStart?.let { it.elapsedNow() < riffDuration } == true) return@runCatching
            playChimeNotes(DayNiteRiff)
            riffStart = TimeSource.Monotonic.markNow()
        }
    }
}
