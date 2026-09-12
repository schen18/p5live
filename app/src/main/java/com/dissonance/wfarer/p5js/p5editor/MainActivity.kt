package com.dissonance.wfarer.p5js.p5editor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dissonance.wfarer.p5js.BuildConfig
import com.dissonance.wfarer.p5js.R
import com.dissonance.wfarer.p5js.audio.AudioInputManager
import java.io.OutputStream

/**
 * Native Android Controller for p5.js Live Coding Suite
 * Manages hardware-accelerated WebGL WebView, CodeMirror 6 interaction, Microphone Audio Recording, and JS Bridge communication.
 */
class MainActivity : AppCompatActivity(), AndroidBridge.BridgeListener {

    private lateinit var webView: WebView
    private lateinit var tvStatus: TextView
    private lateinit var tvFps: TextView
    private lateinit var btnRun: Button
    private lateinit var btnStop: Button
    private lateinit var btnToggleEditor: Button
    private lateinit var btnPresets: Button

    private var bridge: AndroidBridge? = null
    private var audioInputManager: AudioInputManager? = null
    private var isEditorVisible = true
    private var pendingBitmapToSave: Bitmap? = null

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pendingBitmapToSave?.let { saveBitmapToGallery(it) }
        } else {
            Toast.makeText(this, "Storage permission required to save images", Toast.LENGTH_SHORT).show()
        }
        pendingBitmapToSave = null
    }

    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val started = audioInputManager?.startRecording() ?: false
            if (started) {
                Toast.makeText(this, "Microphone active - audio reactivity enabled", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Microphone permission denied for audio visualizer", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupAudioInput()

        val toolbar = findViewById<View>(R.id.toolbar)
        val statusBar = findViewById<View>(R.id.status_bar)

        val initialToolbarTopPadding = toolbar?.paddingTop ?: 0
        val initialStatusBarBottomPadding = statusBar?.paddingBottom ?: 0

        val mainLayout = findViewById<View>(R.id.main_layout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            toolbar?.setPadding(
                toolbar.paddingLeft,
                systemBars.top + initialToolbarTopPadding,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )

            statusBar?.setPadding(
                statusBar.paddingLeft,
                statusBar.paddingTop,
                statusBar.paddingRight,
                systemBars.bottom + initialStatusBarBottomPadding
            )

            insets
        }

        initViews()
        setupWebView()
    }

    private fun setupAudioInput() {
        audioInputManager = AudioInputManager { volume, spectrumJson ->
            runOnUiThread {
                evalJS("if (window.updateAudioData) window.updateAudioData($volume, $spectrumJson);")
            }
        }
    }

    private fun initViews() {
        webView = findViewById(R.id.web_view)
        tvStatus = findViewById(R.id.tv_status)
        tvFps = findViewById(R.id.tv_fps)
        btnRun = findViewById(R.id.btn_run)
        btnStop = findViewById(R.id.btn_stop)
        btnToggleEditor = findViewById(R.id.btn_toggle_editor)
        btnPresets = findViewById(R.id.btn_presets)

        btnRun.setOnClickListener {
            evalJS("window.runSketch();")
            tvStatus.text = getString(R.string.status_running)
            tvStatus.setTextColor(getColor(R.color.status_active))
        }

        btnStop.setOnClickListener {
            evalJS("window.stopSketch();")
            audioInputManager?.stopRecording()
            tvStatus.text = getString(R.string.status_stopped)
            tvStatus.setTextColor(getColor(R.color.status_stopped))
        }

        btnToggleEditor.setOnClickListener {
            isEditorVisible = !isEditorVisible
            evalJS("window.toggleEditorVisibility($isEditorVisible);")
        }

        btnPresets.setOnClickListener {
            showPresetsDialog()
        }

        btnRun.setOnLongClickListener {
            evalJS("window.exportCanvasData();")
            true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        bridge = AndroidBridge(this)
        webView.addJavascriptInterface(bridge!!, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tvStatus.text = getString(R.string.status_engine_loaded)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    tvStatus.text = getString(R.string.status_load_error)
                    tvStatus.setTextColor(getColor(R.color.status_error))
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return

                // Granting WebView capture without the OS-level runtime permission
                // makes Chromium open the mic unprivileged and fail; only grant
                // audio capture once the app actually holds RECORD_AUDIO.
                val micPermissionGranted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                val resources = request.resources
                val grantable = resources.all {
                    it != PermissionRequest.RESOURCE_AUDIO_CAPTURE || micPermissionGranted
                }

                if (grantable) {
                    request.grant(resources)
                } else {
                    request.deny()
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun evalJS(script: String) {
        webView.evaluateJavascript(script, null)
    }

    override fun onJSError(errorMsg: String, lineNumber: Int) {
        tvStatus.text = getString(R.string.status_js_error_fmt, lineNumber, errorMsg)
        tvStatus.setTextColor(getColor(R.color.status_error))
    }

    override fun logFromJS(message: String) {
        if (!message.contains("Error")) {
            tvStatus.text = getString(R.string.status_js_log_fmt, message)
            tvStatus.setTextColor(getColor(R.color.status_active))
        }
    }

    override fun onSketchReady() {
        tvStatus.text = getString(R.string.status_engine_ready)
        tvStatus.setTextColor(getColor(R.color.status_ready))
    }

    override fun onFpsReport(fps: Float) {
        val roundedFps = fps.toInt().coerceIn(0, 120)
        tvFps.text = getString(R.string.status_fps_fmt, roundedFps)
        val colorRes = when {
            roundedFps >= 45 -> R.color.status_ready
            roundedFps >= 25 -> R.color.p5_accent_orange
            else -> R.color.status_error
        }
        tvFps.setTextColor(getColor(colorRes))
    }

    override fun onRequestMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val started = audioInputManager?.startRecording() ?: false
            if (!started) {
                Toast.makeText(
                    this,
                    "Mic capture failed to start - another app may be using the microphone",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStopMic() {
        audioInputManager?.stopRecording()
    }

    override fun getAudioVolume(): Float {
        return audioInputManager?.currentVolume ?: 0f
    }

    override fun getAudioSpectrumJson(): String {
        return audioInputManager?.currentSpectrumJson ?: "[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]"
    }

    override fun onCanvasExported(dataUrl: String) {
        if (dataUrl.isBlank() || !dataUrl.contains(",")) {
            Toast.makeText(this, "Export failed: Invalid image data", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val base64Data = dataUrl.substringAfter(",")
            val decodedString = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

            if (bitmap != null) {
                checkAndSaveBitmap(bitmap)
            } else {
                Toast.makeText(this, "Export failed: Image decoding error", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export frame: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndSaveBitmap(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingBitmapToSave = bitmap
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        saveBitmapToGallery(bitmap)
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "p5_sketch_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/p5LiveSuite")
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            Toast.makeText(this, "Canvas exported to Pictures/p5LiveSuite", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Export completed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPresetsDialog() {
        val presets = arrayOf(
            "1. Particle Swarm (Touch Reactive)",
            "2. Cyber Matrix Flow Grid",
            "3. Neon Sine Waves",
            "4. Generative Constellation Field",
            "5. Mic Audio Spectrum Pulsar Grid"
        )

        AlertDialog.Builder(this)
            .setTitle("Select Generative Preset")
            .setItems(presets) { _, which ->
                val code = when (which) {
                    0 -> PRESET_PARTICLES
                    1 -> PRESET_CYBER_GRID
                    2 -> PRESET_NEON_WAVES
                    3 -> PRESET_CONSTELLATION
                    else -> PRESET_SPECTRUM_GRID
                }
                val base64Code = Base64.encodeToString(code.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                evalJS("window.setCodeFromBase64('$base64Code');")
                tvStatus.text = getString(R.string.status_loaded_preset_fmt, presets[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        audioInputManager?.stopRecording()
        webView.stopLoading()
        webView.removeJavascriptInterface("AndroidBridge")
        webView.loadUrl("about:blank")
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val PRESET_PARTICLES = """// Interactive Particle Swarm
let particles = [];
let colorHue = 0;

function setup() {
  createCanvas(windowWidth, windowHeight);
  colorMode(HSB, 360, 100, 100, 1);
  for (let i = 0; i < 60; i++) {
    particles.push(new Particle(random(width), random(height)));
  }
}

function draw() {
  background(15, 0.15);
  colorHue = (colorHue + 0.5) % 360;

  let targetX = mouseIsPressed ? mouseX : width / 2 + cos(frameCount * 0.03) * 120;
  let targetY = mouseIsPressed ? mouseY : height / 2 + sin(frameCount * 0.03) * 120;

  noFill();
  stroke((colorHue + 180) % 360, 80, 100, 0.4);
  strokeWeight(2);
  circle(targetX, targetY, 30 + sin(frameCount * 0.1) * 10);

  for (let i = 0; i < particles.length; i++) {
    particles[i].update(targetX, targetY);
    particles[i].display();
  }
}

class Particle {
  constructor(x, y) {
    this.x = x; this.y = y;
    this.vx = random(-2, 2); this.vy = random(-2, 2);
    this.size = random(6, 12); this.hue = random(360);
  }
  update(tx, ty) {
    let dx = tx - this.x, dy = ty - this.y;
    let d = sqrt(dx * dx + dy * dy);
    if (d > 0) { this.vx += (dx / d) * 0.15; this.vy += (dy / d) * 0.15; }
    this.vx *= 0.96; this.vy *= 0.96;
    this.x += this.vx; this.y += this.vy;
    this.hue = (this.hue + 1) % 360;
  }
  display() {
    noStroke(); fill(this.hue, 80, 100, 0.9);
    circle(this.x, this.y, this.size);
  }
}"""

        private const val PRESET_CYBER_GRID = """// Cyber Matrix Flow Grid
function setup() {
  createCanvas(windowWidth, windowHeight);
  background(10);
}

function draw() {
  background(10, 20);
  let cols = 16;
  let rows = 24;
  let w = width / cols;
  let h = height / rows;

  for (let i = 0; i < cols; i++) {
    for (let j = 0; j < rows; j++) {
      let x = i * w + w / 2;
      let y = j * h + h / 2;
      let angle = noise(i * 0.1, j * 0.1, frameCount * 0.02) * TWO_PI * 2;
      
      push();
      translate(x, y);
      rotate(angle);
      stroke(100, 220, 255, 180);
      strokeWeight(2);
      line(-10, 0, 10, 0);
      pop();
    }
  }
}"""

        private const val PRESET_NEON_WAVES = """// Neon Wave Synthesizer
function setup() {
  createCanvas(windowWidth, windowHeight);
  colorMode(HSB, 360, 100, 100, 1);
}

function draw() {
  background(220, 80, 10, 0.2);
  noFill();

  for (let i = 0; i < 5; i++) {
    stroke((frameCount + i * 40) % 360, 80, 100, 0.8);
    strokeWeight(3);
    beginShape();
    for (let x = 0; x <= width; x += 20) {
      let y = height / 2 + sin(x * 0.01 + frameCount * 0.03 + i) * (80 + i * 20);
      vertex(x, y);
    }
    endShape();
  }
}"""

        private const val PRESET_CONSTELLATION = """// Generative Constellation Field
let stars = [];

function setup() {
  createCanvas(windowWidth, windowHeight);
  for (let i = 0; i < 50; i++) {
    stars.push({
      x: random(width),
      y: random(height),
      vx: random(-1, 1),
      vy: random(-1, 1)
    });
  }
}

function draw() {
  background(5, 10, 25);

  for (let i = 0; i < stars.length; i++) {
    let s = stars[i];
    s.x += s.vx; s.y += s.vy;
    if (s.x < 0 || s.x > width) s.vx *= -1;
    if (s.y < 0 || s.y > height) s.vy *= -1;

    fill(255); noStroke();
    circle(s.x, s.y, 4);

    for (let j = i + 1; j < stars.length; j++) {
      let s2 = stars[j];
      let d = dist(s.x, s.y, s2.x, s2.y);
      if (d < 100) {
        stroke(140, 200, 255, map(d, 0, 100, 255, 0));
        strokeWeight(1);
        line(s.x, s.y, s2.x, s2.y);
      }
    }
  }
}"""

        private const val PRESET_SPECTRUM_GRID = """// Mic Audio Spectrum Pulsar Grid
let mic;

function setup() {
  createCanvas(windowWidth, windowHeight);
  colorMode(HSB, 360, 100, 100, 1);
  mic = new p5.AudioIn();
  mic.start();
}

function draw() {
  background(10, 0.3);
  let level = mic.getLevel();
  let spectrum = mic.getSpectrum();

  let cols = 4;
  let rows = 4;
  let cellW = width / cols;
  let cellH = height / rows;

  for (let i = 0; i < cols; i++) {
    for (let j = 0; j < rows; j++) {
      let index = (i + j * cols) % spectrum.length;
      let amp = spectrum[index] || 0;

      let cx = i * cellW + cellW / 2;
      let cy = j * cellH + cellH / 2;
      let size = (cellW * 0.3) + amp * (cellW * 0.6) + level * 30;

      let hue = (index * 22 + frameCount + level * 100) % 360;

      noFill();
      stroke(hue, 80, 100, 0.8);
      strokeWeight(2 + amp * 5);
      rectMode(CENTER);
      rect(cx, cy, size, size, 12);

      fill(hue, 90, 100, 0.9);
      noStroke();
      circle(cx, cy, 8 + level * 20);
    }
  }
}"""
    }
}
