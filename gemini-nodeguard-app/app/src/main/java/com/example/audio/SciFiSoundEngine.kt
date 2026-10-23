package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Built-In Procedural Sci-Fi Audio Engine (SciFiSoundEngine):
 * Zero-dependency real-time PCM waveform synthesizer (AudioTrack 16-bit PCM + ToneGenerator fallback + Haptics).
 * Works 100% offline with zero external MP3 assets.
 * All audio synthesis and playback operations are wrapped in safe try/catch(Throwable) blocks
 * and gated behind user interaction so autoplay/hardware limits never block or crash the UI.
 */
object SciFiSoundEngine {

    private const val SAMPLE_RATE = 24000
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val isMuted = AtomicBoolean(false)
    private val hasUserInteracted = AtomicBoolean(false)

    fun setSoundEnabled(enabled: Boolean) {
        isMuted.set(!enabled)
        if (enabled) {
            hasUserInteracted.set(true)
        }
    }

    fun isSoundEnabled(): Boolean = !isMuted.get()

    fun markUserInteraction() {
        hasUserInteracted.set(true)
    }

    /**
     * 1. Radar Sonar Pulse (playRadarSweep):
     * A deep, resonant sub-bass cosmic sonar ping (220Hz -> 880Hz sine wave with exponential decay
     * and cosmic reverb tail) synced with the central Shield Radar sweep.
     */
    fun playRadarSweep() {
        markUserInteraction()
        if (isMuted.get()) return
        audioScope.launch {
            try {
                val durationMs = 340
                val numSamples = (SAMPLE_RATE * durationMs) / 1000
                val samples = ShortArray(numSamples)
                var phase = 0.0
                var subPhase = 0.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / numSamples.toDouble()
                    // Rising sonar harmonic sweep 220Hz -> 880Hz with sub-bass foundation
                    val freq = 220.0 + (660.0 * (1.0 - exp(-3.2 * t)))
                    val subFreq = 110.0 + 110.0 * t
                    phase += (2.0 * PI * freq) / SAMPLE_RATE
                    subPhase += (2.0 * PI * subFreq) / SAMPLE_RATE

                    val attack = (t / 0.06).coerceAtMost(1.0)
                    val primaryDecay = exp(-4.8 * t)
                    // Echo reverb tail around t = 0.45
                    val reverbTail = if (t > 0.38) 0.32 * exp(-6.5 * (t - 0.38)) * sin(phase * 1.5) else 0.0

                    val rawWave = (0.68 * sin(phase) + 0.32 * sin(subPhase)) * attack * primaryDecay + reverbTail
                    samples[i] = (rawWave.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.48).toInt().toShort()
                }
                playPcmBuffer(samples)
            } catch (_: Throwable) {
                playFallbackTone(ToneGenerator.TONE_PROP_BEEP, 80)
            }
        }
    }

    /**
     * 2. Holographic UI Tap & Tab Switch (playHoloClick):
     * A crisp, futuristic high-tech chirp (1400Hz -> 2400Hz fast triangle wave sweep, 45ms)
     * whenever any button, filter chip, or navigation tab is pressed.
     */
    fun playHoloClick() {
        markUserInteraction()
        if (isMuted.get()) return
        audioScope.launch {
            try {
                val durationMs = 45
                val numSamples = (SAMPLE_RATE * durationMs) / 1000
                val samples = ShortArray(numSamples)
                var phase = 0.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / numSamples.toDouble()
                    val freq = 1400.0 + (2400.0 - 1400.0) * t
                    phase += freq / SAMPLE_RATE
                    val frac = phase - kotlin.math.floor(phase)
                    // Triangle wave: 4 * |frac - 0.5| - 1
                    val triangle = 4.0 * kotlin.math.abs(frac - 0.5) - 1.0
                    val envelope = ((t / 0.12).coerceAtMost(1.0)) * exp(-3.8 * t)
                    samples[i] = ((triangle * envelope).coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.36).toInt().toShort()
                }
                playPcmBuffer(samples)
            } catch (_: Throwable) {
                playFallbackTone(ToneGenerator.TONE_PROP_ACK, 35)
            }
        }
    }

    /**
     * 3. Multi-Tier Stage Lock-In (playStageSuccess):
     * An ascending harmonic telemetry arpeggio (C5 -> E5 -> G5 -> B5) as each of the 4
     * Google/Gemini validation stages completes.
     */
    fun playStageSuccess(stageIndex: Int = -1) {
        markUserInteraction()
        if (isMuted.get()) return
        audioScope.launch {
            try {
                // C5 (523.25Hz), E5 (659.25Hz), G5 (783.99Hz), B5 (987.77Hz)
                val notes = doubleArrayOf(523.25, 659.25, 783.99, 987.77)
                if (stageIndex in 0..3) {
                    val noteFreq = notes[stageIndex]
                    val samples = synthesizeHarmonicNote(noteFreq, durationMs = 115, amplitude = 0.42)
                    playPcmBuffer(samples)
                } else {
                    // Play rapid ascending 4-note arpeggio C5 -> E5 -> G5 -> B5
                    val totalMs = 280
                    val numSamples = (SAMPLE_RATE * totalMs) / 1000
                    val samples = ShortArray(numSamples)
                    val segmentSamples = numSamples / 4
                    var phase = 0.0

                    for (i in 0 until numSamples) {
                        val noteIdx = (i / segmentSamples).coerceIn(0, 3)
                        val localT = (i % segmentSamples).toDouble() / segmentSamples.toDouble()
                        val freq = notes[noteIdx]
                        phase += (2.0 * PI * freq) / SAMPLE_RATE
                        val env = ((localT / 0.1).coerceAtMost(1.0)) * exp(-2.8 * localT)
                        val wave = (0.75 * sin(phase) + 0.25 * sin(phase * 2.0)) * env
                        samples[i] = (wave.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.40).toInt().toShort()
                    }
                    playPcmBuffer(samples)
                }
            } catch (_: Throwable) {
                playFallbackTone(ToneGenerator.TONE_PROP_BEEP2, 90)
            }
        }
    }

    /**
     * 4. Subscription Node Verified Chime (playGeminiVerified):
     * A shimmering, crystalline space chord when a proxy node passes the Google Gemini 200 OK check.
     */
    fun playGeminiVerified() {
        markUserInteraction()
        if (isMuted.get()) return
        audioScope.launch {
            try {
                val durationMs = 420
                val numSamples = (SAMPLE_RATE * durationMs) / 1000
                val samples = ShortArray(numSamples)
                // Cmaj9 crystalline chord: C5, E5, G5, B5, D6
                val chordFreqs = doubleArrayOf(523.25, 659.25, 783.99, 987.77, 1174.66)
                val phases = DoubleArray(chordFreqs.size)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / numSamples.toDouble()
                    val attack = (t / 0.08).coerceAtMost(1.0)
                    val decay = exp(-3.4 * t)
                    // Subtle crystalline shimmer LFO (14Hz)
                    val shimmer = 0.85 + 0.15 * sin(2.0 * PI * 14.0 * t)

                    var sum = 0.0
                    for (n in chordFreqs.indices) {
                        phases[n] += (2.0 * PI * chordFreqs[n]) / SAMPLE_RATE
                        sum += sin(phases[n])
                    }
                    val normalized = (sum / chordFreqs.size) * attack * decay * shimmer
                    samples[i] = (normalized.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.46).toInt().toShort()
                }
                playPcmBuffer(samples)
            } catch (_: Throwable) {
                playFallbackTone(ToneGenerator.TONE_PROP_ACK, 120)
            }
        }
    }

    /**
     * 5. Geo-Block / SSRF Threat Alert (playThreatBlocked):
     * A low-frequency tactical cyber-warning buzz (160Hz -> 110Hz sawtooth pulse)
     * when a 403 Geo-Blocked node or SSRF Exploit is intercepted.
     */
    fun playThreatBlocked() {
        markUserInteraction()
        if (isMuted.get()) return
        audioScope.launch {
            try {
                val durationMs = 290
                val numSamples = (SAMPLE_RATE * durationMs) / 1000
                val samples = ShortArray(numSamples)
                var phase = 0.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / numSamples.toDouble()
                    // Descending sawtooth sweep 160Hz -> 110Hz with dual-pulse gate
                    val freq = 160.0 - (50.0 * t)
                    phase += freq / SAMPLE_RATE
                    val frac = phase - kotlin.math.floor(phase)
                    val sawtooth = 2.0 * frac - 1.0
                    val pulseGate = if (t in 0.42..0.54) 0.15 else 1.0
                    val envelope = exp(-2.2 * t) * pulseGate
                    samples[i] = ((sawtooth * envelope).coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.42).toInt().toShort()
                }
                playPcmBuffer(samples)
            } catch (_: Throwable) {
                playFallbackTone(ToneGenerator.TONE_SUP_ERROR, 140)
            }
        }
    }

    /**
     * 6. Per-Link Copy Confirmation (playCopyLaser):
     * A sleek quantum-confirm zip sound + haptic vibration feedback when the user clicks
     * "Copy Link" on any working Google node.
     */
    fun playCopyLaser(context: Context? = null) {
        markUserInteraction()
        if (context != null) {
            triggerHapticPulse(context)
        }
        if (isMuted.get()) return
        audioScope.launch {
            try {
                val durationMs = 110
                val numSamples = (SAMPLE_RATE * durationMs) / 1000
                val samples = ShortArray(numSamples)
                var phase = 0.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / numSamples.toDouble()
                    // Quantum laser zip 920Hz -> 2960Hz
                    val freq = 920.0 + 2040.0 * (t * t)
                    phase += (2.0 * PI * freq) / SAMPLE_RATE
                    val env = ((t / 0.08).coerceAtMost(1.0)) * exp(-4.2 * t)
                    val sample = (0.75 * sin(phase) + 0.25 * sin(phase * 1.5)) * env
                    samples[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.40).toInt().toShort()
                }
                playPcmBuffer(samples)
            } catch (_: Throwable) {
                playFallbackTone(ToneGenerator.TONE_PROP_ACK, 60)
            }
        }
    }

    private fun synthesizeHarmonicNote(freqHz: Double, durationMs: Int, amplitude: Double): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples.toDouble()
            phase += (2.0 * PI * freqHz) / SAMPLE_RATE
            val attack = (t / 0.08).coerceAtMost(1.0)
            val decay = exp(-3.5 * t)
            val wave = (0.72 * sin(phase) + 0.28 * sin(phase * 2.0)) * attack * decay
            samples[i] = (wave.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * amplitude).toInt().toShort()
        }
        return samples
    }

    private suspend fun playPcmBuffer(samples: ShortArray) {
        if (!hasUserInteracted.get() || isMuted.get() || samples.isEmpty()) return
        var audioTrack: AudioTrack? = null
        try {
            val byteSize = samples.size * 2
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(byteSize)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            val durationMs = ((samples.size * 1000L) / SAMPLE_RATE).coerceIn(30L, 600L)
            delay(durationMs + 25L)
        } catch (_: Throwable) {
            // Safe no-op if audio device is unavailable in container/emulator
        } finally {
            try {
                audioTrack?.stop()
            } catch (_: Throwable) {
            }
            try {
                audioTrack?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun playFallbackTone(toneType: Int, durationMs: Int) {
        if (!hasUserInteracted.get() || isMuted.get()) return
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
            toneGen.startTone(toneType, durationMs)
            audioScope.launch {
                delay(durationMs.toLong() + 40L)
                try {
                    toneGen.release()
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
            // Ignore on headless audio drivers
        }
    }

    private fun triggerHapticPulse(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(24L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(24L)
                }
            }
        } catch (_: Throwable) {
            // Safe fallback if vibrator service is restricted
        }
    }
}
