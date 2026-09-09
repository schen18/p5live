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
 * Captures PCM audio from microphone, computes RMS volume level and 16-band FFT frequency spectrum.
 */
class AudioInputManager(
    private val onAudioFrame: (volume: Float, spectrumJson: String) -> Unit
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

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording.get()) return true

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = max(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            2048
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed.")
                return false
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            recordThread = Thread {
                val buffer = ShortArray(1024)
                var smoothedVolume = 0f

                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Compute RMS Volume Level
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / read)
                        val rawVolume = min(1.0f, (rms / 8000.0).toFloat())

                        // Attack & Decay smoothing for natural audio meter response
                        smoothedVolume = if (rawVolume > smoothedVolume) {
                            smoothedVolume * 0.4f + rawVolume * 0.6f
                        } else {
                            smoothedVolume * 0.88f + rawVolume * 0.12f
                        }
                        currentVolume = smoothedVolume

                        // Compute 16-Band Frequency Spectrum using Goertzel / Simple DFT
                        computeSpectrumBands(buffer, read, sampleRate)

                        val json = buildSpectrumJson(bandValues)
                        currentSpectrumJson = json

                        onAudioFrame(currentVolume, currentSpectrumJson)
                    }

                    try {
                        Thread.sleep(25) // ~40 FPS audio streaming rate
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }.apply { start() }

            Log.i(TAG, "AudioRecord mic capture started successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
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

        for (i in 0 until 16) {
            val freq = targetFreqs[i]
            val mag = goertzelMagnitude(buffer, size, sampleRate.toFloat(), freq)
            val normalizedMag = min(1.0f, (mag / 3500.0f))

            bandValues[i] = if (normalizedMag > bandValues[i]) {
                bandValues[i] * 0.3f + normalizedMag * 0.7f
            } else {
                bandValues[i] * 0.82f + normalizedMag * 0.18f
            }
        }
    }

    private fun goertzelMagnitude(buffer: ShortArray, N: Int, sampleRate: Float, targetFreq: Float): Float {
        val k = (0.5f + (N * targetFreq) / sampleRate).toInt()
        val omega = ((2.0 * Math.PI * k) / N).toFloat()
        val coeff = 2.0f * cos(omega)

        var q0: Float
        var q1 = 0.0f
        var q2 = 0.0f

        for (i in 0 until N) {
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
