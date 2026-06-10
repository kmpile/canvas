package com.kmpile.canvas

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sound.midi.MidiChannel
import javax.sound.midi.MidiSystem

// One daemon thread: opens the synth (which can stall briefly loading the soundbank) and fires
// note on/off off the UI thread, so the easter egg never hitches the playground.
private val scheduler =
    Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "day-nite-chime").apply { isDaemon = true }
    }

// Lazily-opened software synth, reused across notes. GM program 80 = Square Lead — the voice the
// source MIDI uses for this riff, closest to the original track's synth hook.
private val chimeChannel: MidiChannel? by lazy {
    runCatching {
        MidiSystem
            .getSynthesizer()
            .apply { open() }
            .channels
            .firstOrNull()
            ?.apply { programChange(80) }
    }.getOrNull()
}

internal actual fun playChimeNotes(notes: List<ChimeNote>) {
    scheduler.execute {
        val channel = chimeChannel ?: return@execute
        notes.forEach { note ->
            scheduler.schedule({ channel.noteOn(note.midi, 100) }, note.startMillis, TimeUnit.MILLISECONDS)
            scheduler.schedule(
                { channel.noteOff(note.midi) },
                note.startMillis + note.durationMillis,
                TimeUnit.MILLISECONDS,
            )
        }
    }
}
