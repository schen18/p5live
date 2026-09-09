package com.dissonance.wfarer.p5js.p5editor

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * Bi-Directional Native Android <-> WebView Javascript Bridge
 * Enables streaming errors, console output, FPS metrics, mic audio data, and sketch state from CodeMirror 6 and p5.js runtime.
 */
class AndroidBridge(
    private val listener: BridgeListener? = null
) {

    interface BridgeListener {
        fun onJSError(errorMsg: String, lineNumber: Int)
        fun logFromJS(message: String)
        fun onSketchReady()
        fun onCanvasExported(dataUrl: String)
        fun onFpsReport(fps: Float)
        fun onRequestMicPermissionAndStart()
        fun onStopMic()
        fun getAudioVolume(): Float
        fun getAudioSpectrumJson(): String
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onJSError(errorMsg: String, lineNumber: Int) {
        Log.e(TAG, "JS Error [Line $lineNumber]: $errorMsg")
        mainHandler.post {
            listener?.onJSError(errorMsg, lineNumber)
        }
    }

    @JavascriptInterface
    fun logFromJS(message: String) {
        Log.d(TAG, "JS Log: $message")
        mainHandler.post {
            listener?.logFromJS(message)
        }
    }

    @JavascriptInterface
    fun onSketchReady() {
        Log.i(TAG, "p5.js and CodeMirror 6 are fully mounted and ready.")
        mainHandler.post {
            listener?.onSketchReady()
        }
    }

    @JavascriptInterface
    fun onCanvasExported(dataUrl: String) {
        Log.i(TAG, "Canvas frame exported successfully.")
        mainHandler.post {
            listener?.onCanvasExported(dataUrl)
        }
    }

    @JavascriptInterface
    fun onFpsReport(fps: Float) {
        mainHandler.post {
            listener?.onFpsReport(fps)
        }
    }

    @JavascriptInterface
    fun startMic() {
        Log.i(TAG, "JS requested microphone start.")
        mainHandler.post {
            listener?.onRequestMicPermissionAndStart()
        }
    }

    @JavascriptInterface
    fun stopMic() {
        Log.i(TAG, "JS requested microphone stop.")
        mainHandler.post {
            listener?.onStopMic()
        }
    }

    @JavascriptInterface
    fun getAudioVolume(): Float {
        return listener?.getAudioVolume() ?: 0f
    }

    @JavascriptInterface
    fun getAudioSpectrum(): String {
        return listener?.getAudioSpectrumJson() ?: "[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]"
    }

    companion object {
        private const val TAG = "p5LiveCodingBridge"
    }
}
