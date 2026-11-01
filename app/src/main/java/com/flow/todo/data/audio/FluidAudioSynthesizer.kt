package com.flow.todo.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.flow.todo.domain.model.AmbientSoundscape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.sin

/**
 * High-performance, zero-asset procedural ambient sound synthesizer.
 * Generates continuous organic soundscapes via mathematical DSP and AudioTrack.
 * No asset files or network downloads required; runs 100% offline with minimal CPU/battery footprint.
 */
class FluidAudioSynthesizer {

    private var audioTrack: AudioTrack? = null
    private var synthesisJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate / 4)

    @Volatile
    private var currentSoundscape: AmbientSoundscape = AmbientSoundscape.OFF

    @Volatile
    private var isPlaying: Boolean = false

    fun play(soundscape: AmbientSoundscape) {
        if (soundscape == AmbientSoundscape.OFF) {
            stop()
            return
        }

        currentSoundscape = soundscape
        if (isPlaying && synthesisJob?.isActive == true) {
            // Already active, the synthesis loop will transition to the new soundscape algorithm
            return
        }

        stop()
        isPlaying = true

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        synthesisJob = scope.launch {
            val random = Random()
            val chunkSamples = 2048 // stereo: 4096 shorts
            val buffer = ShortArray(chunkSamples * 2)

            // DSP states
            var brownL = 0.0
            var brownR = 0.0
            var streamFilterL = 0.0
            var streamFilterR = 0.0
            var streamLfoPhase = 0.0
            var rainFilterL = 0.0
            var rainFilterR = 0.0
            var binauralPhaseL = 0.0
            var binauralPhaseR = 0.0

            val alphaFreqL = 200.0
            val alphaFreqR = 210.0 // 10Hz binaural delta (Alpha flow state)

            while (isActive && isPlaying) {
                val mode = currentSoundscape
                if (mode == AmbientSoundscape.OFF) {
                    buffer.fill(0)
                    track.write(buffer, 0, buffer.size)
                    continue
                }

                for (i in 0 until chunkSamples) {
                    val idx = i * 2
                    when (mode) {
                        AmbientSoundscape.BROWN_NOISE -> {
                            val whiteL = (random.nextDouble() * 2.0 - 1.0)
                            val whiteR = (random.nextDouble() * 2.0 - 1.0)
                            // Leaky integrator produces true 1/f^2 Brown Noise
                            brownL = (brownL * 0.985) + (whiteL * 0.065)
                            brownR = (brownR * 0.985) + (whiteR * 0.065)
                            buffer[idx] = (brownL * 18000).toInt().coerceIn(-32767, 32767).toShort()
                            buffer[idx + 1] = (brownR * 18000).toInt().coerceIn(-32767, 32767).toShort()
                        }

                        AmbientSoundscape.RAIN_SHOWER -> {
                            val whiteL = (random.nextDouble() * 2.0 - 1.0)
                            val whiteR = (random.nextDouble() * 2.0 - 1.0)
                            // Gentle background rain hiss (bandpass/lowpass)
                            rainFilterL = rainFilterL * 0.72 + whiteL * 0.28
                            rainFilterR = rainFilterR * 0.72 + whiteR * 0.28

                            // Random acoustic droplets
                            var dropL = 0.0
                            var dropR = 0.0
                            if (random.nextDouble() < 0.0018) {
                                dropL = (random.nextDouble() * 2.0 - 1.0) * 0.45
                            }
                            if (random.nextDouble() < 0.0018) {
                                dropR = (random.nextDouble() * 2.0 - 1.0) * 0.45
                            }

                            val sampleL = (rainFilterL * 0.45 + dropL).coerceIn(-1.0, 1.0)
                            val sampleR = (rainFilterR * 0.45 + dropR).coerceIn(-1.0, 1.0)
                            buffer[idx] = (sampleL * 22000).toInt().coerceIn(-32767, 32767).toShort()
                            buffer[idx + 1] = (sampleR * 22000).toInt().coerceIn(-32767, 32767).toShort()
                        }

                        AmbientSoundscape.FOREST_STREAM -> {
                            val whiteL = (random.nextDouble() * 2.0 - 1.0)
                            val whiteR = (random.nextDouble() * 2.0 - 1.0)
                            // Undulating LFO around 0.15 Hz for natural water surges
                            streamLfoPhase += (2.0 * Math.PI * 0.15) / sampleRate
                            if (streamLfoPhase > 2.0 * Math.PI) streamLfoPhase -= 2.0 * Math.PI
                            val lfo = 0.55 + 0.35 * sin(streamLfoPhase)

                            streamFilterL = streamFilterL * 0.82 + whiteL * 0.18
                            streamFilterR = streamFilterR * 0.82 + whiteR * 0.18

                            val streamOutL = streamFilterL * lfo
                            val streamOutR = streamFilterR * lfo
                            buffer[idx] = (streamOutL * 21000).toInt().coerceIn(-32767, 32767).toShort()
                            buffer[idx + 1] = (streamOutR * 21000).toInt().coerceIn(-32767, 32767).toShort()
                        }

                        AmbientSoundscape.BINAURAL_ALPHA -> {
                            // 10 Hz beat frequency between left and right ears
                            binauralPhaseL += (2.0 * Math.PI * alphaFreqL) / sampleRate
                            if (binauralPhaseL > 2.0 * Math.PI) binauralPhaseL -= 2.0 * Math.PI

                            binauralPhaseR += (2.0 * Math.PI * alphaFreqR) / sampleRate
                            if (binauralPhaseR > 2.0 * Math.PI) binauralPhaseR -= 2.0 * Math.PI

                            val toneL = sin(binauralPhaseL) * 0.30
                            val toneR = sin(binauralPhaseR) * 0.30

                            // Subtle brown noise cushion underneath
                            val whiteL = (random.nextDouble() * 2.0 - 1.0)
                            val whiteR = (random.nextDouble() * 2.0 - 1.0)
                            brownL = (brownL * 0.988) + (whiteL * 0.05)
                            brownR = (brownR * 0.988) + (whiteR * 0.05)

                            val outL = (toneL + brownL * 0.25).coerceIn(-1.0, 1.0)
                            val outR = (toneR + brownR * 0.25).coerceIn(-1.0, 1.0)
                            buffer[idx] = (outL * 20000).toInt().coerceIn(-32767, 32767).toShort()
                            buffer[idx + 1] = (outR * 20000).toInt().coerceIn(-32767, 32767).toShort()
                        }

                        AmbientSoundscape.OFF -> {
                            buffer[idx] = 0
                            buffer[idx + 1] = 0
                        }
                    }
                }

                track.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stop() {
        isPlaying = false
        synthesisJob?.cancel()
        synthesisJob = null
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {}
        audioTrack = null
    }

    fun release() {
        stop()
    }
}
