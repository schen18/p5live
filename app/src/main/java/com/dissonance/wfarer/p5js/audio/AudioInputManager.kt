package com.dissonance.wfarer.p5js.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time Microphone Audio Processor for p5.js Live Coding Suite
 * Captures PCM audio from microphone, applies adaptive gain scaling,
 * and computes RMS volume level and 16-band FFT frequency spectrum.
 */
class AudioInputManager(
    private val onAudioFrame: ((volume: Float, spectrumJson: String) -> Unit)? = null,
) {

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    @Volatile
    var currentVolume: Float = 0f
        private set

    @Volatile
    var currentSpectrumJson: String = "[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]"
        private set

    private val bandValues = FloatArray(16)
    private var maxPeakRms = 1000.0 // Dynamic peak baseline for auto-gain

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording.get()) return true

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufSize, 2048)

        // Try candidate audio sources for maximum device compatibility
        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT
        )

        var initializedRecord: AudioRecord? = null
        for (source in audioSources) {
            try {
                val rec = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    initializedRecord = rec
                    Log.i(TAG, "AudioRecord initialized with audio source $source")
                    break
                } else {
                    rec.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize AudioRecord with source $source: ${e.message}")
            }
        }

        if (initializedRecord == null) {
            Log.e(TAG, "Could not initialize AudioRecord with any audio source.")
            return false
        }

        audioRecord = initializedRecord
        try {
            audioRecord?.startRecording()
            isRecording.set(true)

            recordThread = Thread {
                val buffer = ShortArray(1024)
                var smoothedVolume = 0f

                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Calculate RMS Volume
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / read)

                        // Dynamic Peak Tracking for Adaptive Mic Sensitivity
                        maxPeakRms = if (rms > maxPeakRms) {
                            rms
                        } else {
                            max(500.0, maxPeakRms * 0.995)
                        }

                        // Normalize raw volume against dynamic peak floor
                        val rawVolume = min(1.0f, (rms / maxPeakRms).toFloat())

                        // Attack & Decay smoothing for natural audio meter response
                        smoothedVolume = if (rawVolume > smoothedVolume) {
                            (smoothedVolume * 0.3f) + (rawVolume * 0.7f)
                        } else {
                            (smoothedVolume * 0.85f) + (rawVolume * 0.15f)
                        }
                        currentVolume = smoothedVolume

                        // Calculate 16-Band Frequency Spectrum
                        computeSpectrumBands(buffer, read, sampleRate)

                        currentSpectrumJson = buildSpectrumJson(bandValues)

                        onAudioFrame?.invoke(currentVolume, currentSpectrumJson)
                    }

                    try {
                        Thread.sleep(25) // ~40 FPS update rate
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }.apply { start() }

            Log.i(TAG, "AudioRecord mic capture thread started.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            return false
        }
    }

    fun stopRecording() {
        isRecording.set(false)
        try {
            recordThread?.interrupt()
            recordThread = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        currentVolume = 0f
        currentSpectrumJson = "[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]"
    }

    private fun computeSpectrumBands(buffer: ShortArray, size: Int, sampleRate: Int) {
        val targetFreqs = floatArrayOf(
            80f, 120f, 200f, 320f, 500f, 800f, 1200f, 1800f,
            2600f, 3600f, 4800f, 6200f, 7800f, 9500f, 11000f, 12500f
        )

        val scaleFactor = max(1000.0f, maxPeakRms.toFloat() * 0.5f)

        for (i in 0 until 16) {
            val freq = targetFreqs[i]
            val mag = goertzelMagnitude(buffer, size, sampleRate.toFloat(), freq)
            val normalizedMag = min(1.0f, (mag / scaleFactor))

            bandValues[i] = if (normalizedMag > bandValues[i]) {
                (bandValues[i] * 0.25f) + (normalizedMag * 0.75f)
            } else {
                (bandValues[i] * 0.8f) + (normalizedMag * 0.2f)
            }
        }
    }

    private fun goertzelMagnitude(buffer: ShortArray, size: Int, sampleRate: Float, targetFreq: Float): Float {
        val k = (0.5f + (size * targetFreq) / sampleRate).toInt()
        val omega = ((2.0 * Math.PI * k) / size).toFloat()
        val coeff = 2.0f * cos(omega)

        var q0: Float
        var q1 = 0.0f
        var q2 = 0.0f

        for (i in 0 until size) {
            q0 = coeff * q1 - q2 + buffer[i].toFloat()
            q2 = q1
            q1 = q0
        }

        val real = q1 - q2 * cos(omega)
        val imag = q2 * sin(omega)
        return sqrt((real * real + imag * imag).toDouble()).toFloat()
    }

    private fun buildSpectrumJson(bands: FloatArray): String {
        val sb = StringBuilder("[")
        for (i in bands.indices) {
            sb.append(String.format(Locale.US, "%.3f", bands[i]))
            if (i < bands.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    companion object {
        private const val TAG = "AudioInputManager"
    }
}
